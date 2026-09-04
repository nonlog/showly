package com.michaldrabik.ui_base.floppy

import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_local.database.model.WatchlistMovie
import com.michaldrabik.data_local.database.model.WatchlistShow
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyBridgeRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyBridgeTrackedMedia
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyWatchlistType
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.Movie
import com.michaldrabik.data_remote.trakt.model.Show
import com.michaldrabik.data_remote.trakt.model.SyncExportItem
import com.michaldrabik.data_remote.trakt.model.SyncExportRequest
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.repository.bridge.BridgeConflictResolver
import com.michaldrabik.repository.bridge.BridgeObservedState
import com.michaldrabik.repository.bridge.BridgeSyncStateRepository
import com.michaldrabik.repository.mappers.Mappers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyBridgeWatchlistRunner @Inject constructor(
  private val traktSource: AuthorizedTraktRemoteDataSource,
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val identityGuard: FloppyBridgeIdentityGuard,
  private val floppySource: FloppyRemoteDataSource,
  private val floppyBridgeSource: FloppyBridgeRemoteDataSource,
  private val bridgeStateRepository: BridgeSyncStateRepository,
) {

  companion object {
    private const val PRESENT = "present"
    private const val DOMAIN_MOVIES = "watchlist_movies"
    private const val DOMAIN_SHOWS = "watchlist_shows"
  }

  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }

    identityGuard.ensureCurrent()

    val observedAt = nowUtcMillis()
    val activity = traktSource.fetchSyncActivity()
    val traktMovies = traktSource.fetchSyncMoviesWatchlist()
    val traktShows = traktSource.fetchSyncShowsWatchlist()
    val floppyMovies = floppyBridgeSource.fetchWatchlist(FloppyWatchlistType.MOVIES)
    val floppyShows = floppyBridgeSource.fetchWatchlist(FloppyWatchlistType.SHOWS)

    return syncType(
      type = FloppyWatchlistType.MOVIES,
      domain = DOMAIN_MOVIES,
      traktItems = traktMovies,
      floppyItems = floppyMovies,
      traktDomainChangedAt = activity.movies.watchlisted_at.toEpochMillis(),
      observedAt = observedAt,
    ) + syncType(
      type = FloppyWatchlistType.SHOWS,
      domain = DOMAIN_SHOWS,
      traktItems = traktShows,
      floppyItems = floppyShows,
      traktDomainChangedAt = activity.shows.watchlisted_at.toEpochMillis(),
      observedAt = observedAt,
    )
  }

  private suspend fun syncType(
    type: FloppyWatchlistType,
    domain: String,
    traktItems: List<SyncItem>,
    floppyItems: List<FloppyBridgeTrackedMedia>,
    traktDomainChangedAt: Long?,
    observedAt: Long,
  ): Int {
    val traktByTmdb = traktItems
      .mapNotNull { item -> item.toWatchlistLookup(type) }
      .associateBy(WatchlistLookup::tmdbId)
    val floppyByTmdb = floppyItems
      .groupBy(FloppyBridgeTrackedMedia::tmdbId)
      .mapValues { (_, entries) -> entries.maxByOrNull(FloppyBridgeTrackedMedia::createdAt)!! }
    val previous = bridgeStateRepository.getAll(domain).associateBy(BridgeSyncState::entityKey)
    val allTmdbIds = buildSet {
      addAll(traktByTmdb.keys)
      addAll(floppyByTmdb.keys)
      addAll(previous.keys.mapNotNull(String::toLongOrNull))
    }

    val traktAdds = mutableListOf<SyncExportItem>()
    val traktRemoves = mutableListOf<SyncExportItem>()
    val pendingStates = mutableListOf<BridgeSyncState>()
    val localActions = mutableListOf<suspend () -> Unit>()
    var changes = 0

    allTmdbIds.sorted().forEach { tmdbId ->
      val entityKey = tmdbId.toString()
      val prior = previous[entityKey]
      val traktEntry = traktByTmdb[tmdbId]
      val floppyEntry = floppyByTmdb[tmdbId]

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

      val traktRemoteChangedAt = when {
        traktEntry != null -> traktEntry.changedAt
        previousTrakt.observed && previousTrakt.value != null -> traktDomainChangedAt
        else -> null
      }
      val traktObserved = BridgeConflictResolver.observe(
        previous = previousTrakt,
        currentValue = PRESENT.takeIf { traktEntry != null },
        remoteChangedAt = traktRemoteChangedAt,
        observedAt = observedAt,
      )

      val floppyTransition = previousFloppy.observed && ((previousFloppy.value != null) != (floppyEntry != null))
      val floppyRemoteChangedAt = when {
        floppyTransition -> floppyBridgeSource.fetchLatestFieldChange(type, tmdbId, "status")
        floppyEntry != null -> floppyEntry.createdAt.takeIf { it > 0 }
        else -> null
      }
      val floppyObserved = BridgeConflictResolver.observe(
        previous = previousFloppy,
        currentValue = PRESENT.takeIf { floppyEntry != null },
        remoteChangedAt = floppyRemoteChangedAt,
        observedAt = observedAt,
      )

      val resolution = BridgeConflictResolver.resolve(
        trakt = traktObserved,
        floppy = floppyObserved,
        previousResolvedValue = prior?.resolvedValue,
        previousResolvedAt = prior?.resolvedChangedAt ?: 0,
      )

      var lookup = traktEntry
      if (resolution.value == PRESENT && lookup == null) {
        lookup = resolveTraktLookup(type, tmdbId)
      }
      var finalTrakt = traktObserved
      var finalFloppy = floppyObserved

      if (resolution.value == PRESENT) {
        if (traktEntry == null && lookup != null) {
          traktAdds += SyncExportItem.create(lookup.traktId)
          finalTrakt = BridgeObservedState(PRESENT, resolution.changedAt, true)
          changes += 1
        }
        if (floppyEntry == null) {
          if (floppyBridgeSource.ensureWatchlistPresent(type, tmdbId)) changes += 1
          finalFloppy = BridgeObservedState(PRESENT, resolution.changedAt, true)
        }
        if (lookup != null) {
          localActions += { ensureLocalPresent(type, lookup, resolution.changedAt) }
        }
      } else {
        if (traktEntry != null) {
          traktRemoves += SyncExportItem.create(traktEntry.traktId)
          finalTrakt = BridgeObservedState(null, resolution.changedAt, true)
          changes += 1
        }
        if (floppyEntry != null) {
          if (floppyBridgeSource.removePlanning(type, tmdbId, floppyEntry.consumptionId)) changes += 1
          finalFloppy = BridgeObservedState(null, resolution.changedAt, true)
        }
        (lookup ?: resolveTraktLookup(type, tmdbId))?.let { resolvedLookup ->
          localActions += { ensureLocalAbsent(type, resolvedLookup.traktId) }
        }
      }

      pendingStates += BridgeSyncState(
        domain = domain,
        entityKey = entityKey,
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

    postTraktWatchlistChanges(type, traktAdds, traktRemoves)
    localActions.forEach { it() }
    pendingStates.forEach { bridgeStateRepository.save(it) }
    Timber.d("Trakt <-> Floppy watchlist bridge for $type completed with $changes change(s)")
    return changes
  }

  private suspend fun postTraktWatchlistChanges(
    type: FloppyWatchlistType,
    adds: List<SyncExportItem>,
    removes: List<SyncExportItem>,
  ) {
    if (adds.isNotEmpty()) {
      traktSource.postSyncWatchlist(type.toRequest(adds))
    }
    if (removes.isNotEmpty()) {
      traktSource.postDeleteWatchlist(type.toRequest(removes))
    }
  }

  private suspend fun resolveTraktLookup(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): WatchlistLookup? {
    val local = when (type) {
      FloppyWatchlistType.MOVIES -> localSource.movies.getByTmdbId(tmdbId)?.let {
        WatchlistLookup(tmdbId = tmdbId, traktId = it.idTrakt, movie = null, show = null, changedAt = 0)
      }
      FloppyWatchlistType.SHOWS -> localSource.shows.getByTmdbId(tmdbId)?.let {
        WatchlistLookup(tmdbId = tmdbId, traktId = it.idTrakt, movie = null, show = null, changedAt = 0)
      }
    }
    if (local != null && local.traktId > 0) return local

    val search = remoteSource.trakt.fetchSearchId("tmdb", tmdbId.toString())
    return when (type) {
      FloppyWatchlistType.MOVIES -> search.firstNotNullOfOrNull { result ->
        result.movie?.ids?.trakt?.takeIf { it > 0 }?.let { traktId ->
          WatchlistLookup(tmdbId, traktId, result.movie, null, 0)
        }
      }
      FloppyWatchlistType.SHOWS -> search.firstNotNullOfOrNull { result ->
        result.show?.ids?.trakt?.takeIf { it > 0 }?.let { traktId ->
          WatchlistLookup(tmdbId, traktId, null, result.show, 0)
        }
      }
    }
  }

  private suspend fun ensureLocalPresent(
    type: FloppyWatchlistType,
    lookup: WatchlistLookup,
    changedAt: Long,
  ) {
    when (type) {
      FloppyWatchlistType.MOVIES -> {
        if (localSource.movies.getById(lookup.traktId) == null && lookup.movie != null) {
          val movie = mappers.movie.fromNetwork(lookup.movie)
          localSource.movies.upsert(listOf(mappers.movie.toDatabase(movie)))
        }
        val occupied = localSource.myMovies.getById(lookup.traktId) != null ||
          localSource.archiveMovies.getById(lookup.traktId) != null
        if (!occupied && !localSource.watchlistMovies.checkExists(lookup.traktId)) {
          localSource.watchlistMovies.insert(WatchlistMovie.fromTraktId(lookup.traktId, changedAt.safeTimestamp()))
        }
      }
      FloppyWatchlistType.SHOWS -> {
        if (localSource.shows.getById(lookup.traktId) == null && lookup.show != null) {
          val show = mappers.show.fromNetwork(lookup.show)
          localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))
        }
        val occupied = localSource.myShows.getById(lookup.traktId) != null ||
          localSource.archiveShows.getById(lookup.traktId) != null
        if (!occupied && !localSource.watchlistShows.checkExists(lookup.traktId)) {
          localSource.watchlistShows.insert(WatchlistShow.fromTraktId(lookup.traktId, changedAt.safeTimestamp()))
        }
      }
    }
  }

  private suspend fun ensureLocalAbsent(
    type: FloppyWatchlistType,
    traktId: Long,
  ) {
    when (type) {
      FloppyWatchlistType.MOVIES -> if (localSource.watchlistMovies.checkExists(traktId)) {
        localSource.watchlistMovies.deleteById(traktId)
      }
      FloppyWatchlistType.SHOWS -> if (localSource.watchlistShows.checkExists(traktId)) {
        localSource.watchlistShows.deleteById(traktId)
      }
    }
  }

  private fun SyncItem.toWatchlistLookup(type: FloppyWatchlistType): WatchlistLookup? =
    when (type) {
      FloppyWatchlistType.MOVIES -> movie?.let { movie ->
        val ids = movie.ids ?: return@let null
        val tmdbId = ids.tmdb?.takeIf { it > 0 } ?: return@let null
        val traktId = ids.trakt?.takeIf { it > 0 } ?: return@let null
        WatchlistLookup(tmdbId, traktId, movie, null, listed_at.toEpochMillis() ?: 0)
      }
      FloppyWatchlistType.SHOWS -> show?.let { show ->
        val ids = show.ids ?: return@let null
        val tmdbId = ids.tmdb?.takeIf { it > 0 } ?: return@let null
        val traktId = ids.trakt?.takeIf { it > 0 } ?: return@let null
        WatchlistLookup(tmdbId, traktId, null, show, listed_at.toEpochMillis() ?: 0)
      }
    }

  private fun FloppyWatchlistType.toRequest(items: List<SyncExportItem>) =
    when (this) {
      FloppyWatchlistType.MOVIES -> SyncExportRequest(movies = items)
      FloppyWatchlistType.SHOWS -> SyncExportRequest(shows = items)
    }

  private fun String?.toEpochMillis(): Long? = this.toUtcDateTime()?.toInstant()?.toEpochMilli()

  private fun Long.safeTimestamp(): Long = takeIf { it > 0 } ?: nowUtcMillis()

  private data class WatchlistLookup(
    val tmdbId: Long,
    val traktId: Long,
    val movie: Movie?,
    val show: Show?,
    val changedAt: Long,
  )
}
