package com.michaldrabik.ui_base.floppy

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyBridgeHistoryEvent
import com.michaldrabik.data_remote.floppy.FloppyBridgeHistoryIdentity
import com.michaldrabik.data_remote.floppy.FloppyBridgeHistoryKind
import com.michaldrabik.data_remote.floppy.FloppyBridgeRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.SyncExportItem
import com.michaldrabik.data_remote.trakt.model.SyncExportRequest
import com.michaldrabik.data_remote.trakt.model.SyncHistoryItem
import com.michaldrabik.repository.bridge.BridgeConflictResolver
import com.michaldrabik.repository.bridge.BridgeObservedState
import com.michaldrabik.repository.bridge.BridgeSyncStateRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyBridgeHistoryRunner @Inject constructor(
  private val traktSource: AuthorizedTraktRemoteDataSource,
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val identityGuard: FloppyBridgeIdentityGuard,
  private val floppySource: FloppyRemoteDataSource,
  private val floppyBridgeSource: FloppyBridgeRemoteDataSource,
  private val bridgeStateRepository: BridgeSyncStateRepository,
) {

  companion object {
    private const val CHUNK_SIZE = 200
    private const val DOMAIN = "history"
    private const val PRESENT = "present"
  }

  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }

    identityGuard.ensureCurrent()

    val observedAt = nowUtcMillis()
    val traktEvents = buildList {
      traktSource.fetchSyncHistory("movies", 0).mapNotNullTo(this) { it.toBridgeEvent() }
      traktSource.fetchSyncHistory("episodes", 0).mapNotNullTo(this) { it.toBridgeEvent() }
    }
    val traktByKey = traktEvents.associateBy(TraktHistoryEvent::key)

    val floppyEvents = buildList {
      floppyBridgeSource.fetchHistoryIdentities().forEach { identity ->
        addAll(floppyBridgeSource.fetchHistoryEvents(identity))
      }
    }
    val floppyByKey = floppyEvents.associateBy(FloppyBridgeHistoryEvent::key)
    val previous = bridgeStateRepository.getAll(DOMAIN).associateBy(BridgeSyncState::entityKey)
    val allKeys = buildSet {
      addAll(traktByKey.keys)
      addAll(floppyByKey.keys)
      addAll(previous.keys)
    }

    val traktMovieAdds = mutableListOf<SyncExportItem>()
    val traktEpisodeAdds = mutableListOf<SyncExportItem>()
    val traktMovieDeletes = mutableListOf<SyncExportItem>()
    val traktEpisodeDeletes = mutableListOf<SyncExportItem>()
    val pendingStates = mutableListOf<BridgeSyncState>()
    val seasonCache = mutableMapOf<Long, Map<Pair<Int, Int>, Long>>()
    var changes = 0

    allKeys.sorted().forEach { key ->
      val prior = previous[key]
      val traktEvent = traktByKey[key]
      val floppyEvent = floppyByKey[key]
      val previousTrakt = BridgeObservedState(
        value = prior?.traktValue,
        changedAt = prior?.traktChangedAt ?: 0,
        observed = prior?.traktObserved ?: false,
      )
      val previousFloppy = BridgeObservedState(
        value = prior?.floppyValue,
        changedAt = prior?.floppyChangedAt ?: 0,
        observed = prior?.floppyObserved ?: false,
      )
      val traktObserved = BridgeConflictResolver.observe(
        previous = previousTrakt,
        currentValue = PRESENT.takeIf { traktEvent != null },
        remoteChangedAt = null,
        observedAt = observedAt,
      )
      val floppyObserved = BridgeConflictResolver.observe(
        previous = previousFloppy,
        currentValue = PRESENT.takeIf { floppyEvent != null },
        remoteChangedAt = null,
        observedAt = observedAt,
      )
      val resolution = BridgeConflictResolver.resolve(
        trakt = traktObserved,
        floppy = floppyObserved,
        previousResolvedValue = prior?.resolvedValue,
        previousResolvedAt = prior?.resolvedChangedAt ?: 0,
      )

      var finalTrakt = traktObserved
      var finalFloppy = floppyObserved
      val identity = traktEvent?.identity ?: floppyEvent?.identity

      if (resolution.value == PRESENT && identity != null) {
        if (traktEvent == null && floppyEvent != null) {
          val traktItemId = resolveTraktItemId(identity, seasonCache)
          if (traktItemId != null) {
            val item = SyncExportItem.create(
              traktId = traktItemId,
              watchedAt = dateIsoStringFromMillis(floppyEvent.watchedAt),
            )
            when (identity.kind) {
              FloppyBridgeHistoryKind.MOVIE -> traktMovieAdds += item
              FloppyBridgeHistoryKind.EPISODE -> traktEpisodeAdds += item
            }
            finalTrakt = BridgeObservedState(PRESENT, resolution.changedAt, true)
            changes += 1
          }
        }
        if (floppyEvent == null && traktEvent != null) {
          ensureFloppyHistory(traktEvent)
          finalFloppy = BridgeObservedState(PRESENT, resolution.changedAt, true)
          changes += 1
        }
      } else if (resolution.value == null) {
        if (traktEvent != null) {
          val item = SyncExportItem.create(
            traktId = traktEvent.traktItemId,
            watchedAt = dateIsoStringFromMillis(traktEvent.watchedAt),
          )
          when (traktEvent.identity.kind) {
            FloppyBridgeHistoryKind.MOVIE -> traktMovieDeletes += item
            FloppyBridgeHistoryKind.EPISODE -> traktEpisodeDeletes += item
          }
          finalTrakt = BridgeObservedState(null, resolution.changedAt, true)
          changes += 1
        }
        if (floppyEvent != null) {
          floppyBridgeSource.removeHistoryEvent(floppyEvent)
          finalFloppy = BridgeObservedState(null, resolution.changedAt, true)
          changes += 1
        }
      }

      pendingStates += BridgeSyncState(
        domain = DOMAIN,
        entityKey = key,
        traktValue = finalTrakt.value,
        traktChangedAt = finalTrakt.changedAt,
        traktObserved = finalTrakt.observed,
        floppyValue = finalFloppy.value,
        floppyChangedAt = finalFloppy.changedAt,
        floppyObserved = finalFloppy.observed,
        resolvedValue = resolution.value,
        resolvedChangedAt = resolution.changedAt,
      )
    }

    traktMovieAdds.chunked(CHUNK_SIZE).forEach { chunk ->
      traktSource.postSyncWatched(SyncExportRequest(movies = chunk))
    }
    traktEpisodeAdds.chunked(CHUNK_SIZE).forEach { chunk ->
      traktSource.postSyncWatched(SyncExportRequest(episodes = chunk))
    }
    traktMovieDeletes.chunked(CHUNK_SIZE).forEach { chunk ->
      traktSource.postDeleteProgress(SyncExportRequest(movies = chunk))
    }
    traktEpisodeDeletes.chunked(CHUNK_SIZE).forEach { chunk ->
      traktSource.postDeleteProgress(SyncExportRequest(episodes = chunk))
    }
    pendingStates.forEach { bridgeStateRepository.save(it) }

    Timber.d(
      "Trakt <-> Floppy history bridge completed with $changes reconciliation change(s); " +
        "history events use observed mutation time for tombstones and preserve independent rewatches",
    )
    return changes
  }

  private suspend fun ensureFloppyHistory(event: TraktHistoryEvent) {
    val watchedAt = dateIsoStringFromMillis(event.watchedAt)
    when (event.identity.kind) {
      FloppyBridgeHistoryKind.MOVIE ->
        floppySource.ensureMovieHistory(event.identity.tmdbId, watchedAt)
      FloppyBridgeHistoryKind.EPISODE -> {
        val season = requireNotNull(event.identity.season)
        val episode = requireNotNull(event.identity.episode)
        floppySource.ensureEpisodeHistory(event.identity.tmdbId, season, episode, watchedAt)
      }
    }
  }

  private suspend fun resolveTraktItemId(
    identity: FloppyBridgeHistoryIdentity,
    seasonCache: MutableMap<Long, Map<Pair<Int, Int>, Long>>,
  ): Long? {
    return when (identity.kind) {
      FloppyBridgeHistoryKind.MOVIE -> resolveMovieTraktId(identity.tmdbId)
      FloppyBridgeHistoryKind.EPISODE -> {
        val showTraktId = resolveShowTraktId(identity.tmdbId) ?: return null
        val season = identity.season ?: return null
        val episode = identity.episode ?: return null
        val episodes = seasonCache.getOrPut(showTraktId) {
          remoteSource.trakt
            .fetchSeasons(showTraktId)
            .flatMap { it.episodes.orEmpty() }
            .mapNotNull { remoteEpisode ->
              val s = remoteEpisode.season ?: return@mapNotNull null
              val e = remoteEpisode.number ?: return@mapNotNull null
              val traktId = remoteEpisode.ids?.trakt ?: return@mapNotNull null
              (s to e) to traktId
            }.toMap()
        }
        episodes[season to episode]
      }
    }
  }

  private suspend fun resolveMovieTraktId(tmdbId: Long): Long? {
    localSource.movies
      .getByTmdbId(tmdbId)
      ?.idTrakt
      ?.takeIf { it > 0 }
      ?.let { return it }
    return remoteSource.trakt
      .fetchSearchId("tmdb", tmdbId.toString())
      .firstNotNullOfOrNull {
        it.movie
          ?.ids
          ?.trakt
          ?.takeIf { id -> id > 0 }
      }
  }

  private suspend fun resolveShowTraktId(tmdbId: Long): Long? {
    localSource.shows
      .getByTmdbId(tmdbId)
      ?.idTrakt
      ?.takeIf { it > 0 }
      ?.let { return it }
    return remoteSource.trakt
      .fetchSearchId("tmdb", tmdbId.toString())
      .firstNotNullOfOrNull {
        it.show
          ?.ids
          ?.trakt
          ?.takeIf { id -> id > 0 }
      }
  }

  private fun SyncHistoryItem.toBridgeEvent(): TraktHistoryEvent? {
    val watchedAt = watched_at.toUtcDateTime()?.toInstant()?.toEpochMilli() ?: return null
    val movieIds = movie?.ids
    if (movieIds != null) {
      val tmdbId = movieIds.tmdb ?: return null
      val traktId = movieIds.trakt ?: return null
      val identity = FloppyBridgeHistoryIdentity(FloppyBridgeHistoryKind.MOVIE, tmdbId)
      return TraktHistoryEvent(identity, traktId, watchedAt)
    }
    val showTmdbId = show?.ids?.tmdb ?: return null
    val episodeTraktId = episode?.ids?.trakt ?: return null
    val season = episode.season ?: return null
    val number = episode.number ?: return null
    return TraktHistoryEvent(
      identity = FloppyBridgeHistoryIdentity(FloppyBridgeHistoryKind.EPISODE, showTmdbId, season, number),
      traktItemId = episodeTraktId,
      watchedAt = watchedAt,
    )
  }

  private data class TraktHistoryEvent(
    val identity: FloppyBridgeHistoryIdentity,
    val traktItemId: Long,
    val watchedAt: Long,
  ) {
    val key: String
      get() = when (identity.kind) {
        FloppyBridgeHistoryKind.MOVIE -> "m:${identity.tmdbId}:$watchedAt"
        FloppyBridgeHistoryKind.EPISODE ->
          "e:${identity.tmdbId}:${identity.season}:${identity.episode}:$watchedAt"
      }
  }

  private val FloppyBridgeHistoryEvent.key: String
    get() = when (identity.kind) {
      FloppyBridgeHistoryKind.MOVIE -> "m:${identity.tmdbId}:$watchedAt"
      FloppyBridgeHistoryKind.EPISODE ->
        "e:${identity.tmdbId}:${identity.season}:${identity.episode}:$watchedAt"
    }
}
