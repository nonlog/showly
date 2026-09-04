package com.michaldrabik.ui_base.floppy

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyBridgeRating
import com.michaldrabik.data_remote.floppy.FloppyBridgeRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyWatchlistType
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.Movie
import com.michaldrabik.data_remote.trakt.model.RatingResultMovie
import com.michaldrabik.data_remote.trakt.model.RatingResultShow
import com.michaldrabik.data_remote.trakt.model.Show
import com.michaldrabik.repository.bridge.BridgeConflictResolver
import com.michaldrabik.repository.bridge.BridgeObservedState
import com.michaldrabik.repository.bridge.BridgeSyncStateRepository
import com.michaldrabik.repository.mappers.Mappers
import timber.log.Timber
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyBridgeRatingsRunner @Inject constructor(
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
    private const val DOMAIN_MOVIES = "ratings_movies"
    private const val DOMAIN_SHOWS = "ratings_shows"
  }

  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }
    identityGuard.ensureCurrent()

    val observedAt = nowUtcMillis()
    val activity = traktSource.fetchSyncActivity()
    val movieRatings = traktSource.fetchMoviesRatings().mapNotNull { it.toBridgeRating() }
    val showRatings = traktSource.fetchShowsRatings().mapNotNull { it.toBridgeRating() }

    return syncType(
      type = FloppyWatchlistType.MOVIES,
      domain = DOMAIN_MOVIES,
      traktRatings = movieRatings,
      traktDomainChangedAt = activity.movies.rated_at.toEpochMillis(),
      observedAt = observedAt,
    ) + syncType(
      type = FloppyWatchlistType.SHOWS,
      domain = DOMAIN_SHOWS,
      traktRatings = showRatings,
      traktDomainChangedAt = activity.shows.rated_at.toEpochMillis(),
      observedAt = observedAt,
    )
  }

  private suspend fun syncType(
    type: FloppyWatchlistType,
    domain: String,
    traktRatings: List<TraktBridgeRating>,
    traktDomainChangedAt: Long?,
    observedAt: Long,
  ): Int {
    val traktByTmdb = traktRatings.associateBy(TraktBridgeRating::tmdbId)
    val floppyByTmdb = floppyBridgeSource.fetchRatings(type).associateBy(FloppyBridgeRating::tmdbId)
    val previous = bridgeStateRepository.getAll(domain).associateBy(BridgeSyncState::entityKey)
    val allTmdbIds = buildSet {
      addAll(traktByTmdb.keys)
      addAll(floppyByTmdb.keys)
      addAll(previous.keys.mapNotNull(String::toLongOrNull))
    }
    var changes = 0

    allTmdbIds.sorted().forEach { tmdbId ->
      val key = tmdbId.toString()
      val prior = previous[key]
      val traktRating = traktByTmdb[tmdbId]
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

      val floppyRating = floppyByTmdb[tmdbId] ?: if (previousFloppy.value != null) {
        floppyBridgeSource.fetchLatestRating(type, tmdbId)
      } else {
        null
      }
      val traktValue = traktRating?.rating?.toString()
      val floppyValue = floppyRating?.score.toTraktRating()?.toString()
      val traktObserved = BridgeConflictResolver.observe(
        previous = previousTrakt,
        currentValue = traktValue,
        remoteChangedAt = when {
          traktRating != null -> traktRating.changedAt
          previousTrakt.value != null -> traktDomainChangedAt
          else -> null
        },
        observedAt = observedAt,
      )
      val floppyObserved = BridgeConflictResolver.observe(
        previous = previousFloppy,
        currentValue = floppyValue,
        remoteChangedAt = floppyRating?.changedAt?.takeIf { it > 0 },
        observedAt = observedAt,
      )
      val resolution = BridgeConflictResolver.resolve(
        trakt = traktObserved,
        floppy = floppyObserved,
        previousResolvedValue = prior?.resolvedValue,
        previousResolvedAt = prior?.resolvedChangedAt ?: 0,
      )
      val resolvedRating = resolution.value?.toIntOrNull()
      var finalTrakt = traktObserved
      var finalFloppy = floppyObserved
      var network = resolveNetwork(type, tmdbId, traktRating?.traktId)

      if (traktValue != resolution.value && network != null) {
        if (resolvedRating == null) {
          deleteTraktRating(type, network)
        } else {
          postTraktRating(type, network, resolvedRating, resolution.changedAt.safeTimestamp())
        }
        finalTrakt = BridgeObservedState(resolution.value, resolution.changedAt, true)
        changes += 1
      }

      if (floppyValue != resolution.value) {
        floppyBridgeSource.setRating(type, tmdbId, resolvedRating?.toDouble())
        finalFloppy = BridgeObservedState(resolution.value, resolution.changedAt, true)
        changes += 1
      }

      if (network == null && (resolvedRating != null || traktRating != null)) {
        network = resolveNetwork(type, tmdbId, traktRating?.traktId)
      }
      updateLocalRating(type, network, resolvedRating, resolution.changedAt.safeTimestamp())

      bridgeStateRepository.save(
        BridgeSyncState(
          domain = domain,
          entityKey = key,
          traktValue = finalTrakt.value,
          traktChangedAt = finalTrakt.changedAt,
          traktObserved = finalTrakt.observed,
          floppyValue = finalFloppy.value,
          floppyChangedAt = finalFloppy.changedAt,
          floppyObserved = finalFloppy.observed,
          resolvedValue = resolution.value,
          resolvedChangedAt = resolution.changedAt,
        ),
      )
    }

    Timber.d("Trakt <-> Floppy ratings bridge for $type completed with $changes change(s)")
    return changes
  }

  private suspend fun resolveNetwork(
    type: FloppyWatchlistType,
    tmdbId: Long,
    knownTraktId: Long?,
  ): Any? =
    when (type) {
      FloppyWatchlistType.MOVIES -> {
        if (knownTraktId != null) {
          remoteSource.trakt.fetchMovie(knownTraktId)
        } else {
          remoteSource.trakt
            .fetchSearchId("tmdb", tmdbId.toString())
            .firstNotNullOfOrNull { it.movie }
        }
      }
      FloppyWatchlistType.SHOWS -> {
        if (knownTraktId != null) {
          remoteSource.trakt.fetchShow(knownTraktId)
        } else {
          remoteSource.trakt
            .fetchSearchId("tmdb", tmdbId.toString())
            .firstNotNullOfOrNull { it.show }
        }
      }
    }

  private suspend fun postTraktRating(
    type: FloppyWatchlistType,
    network: Any,
    rating: Int,
    changedAt: Long,
  ) {
    val ratedAt = dateIsoStringFromMillis(changedAt)
    when (type) {
      FloppyWatchlistType.MOVIES -> traktSource.postRating(network as Movie, rating, ratedAt)
      FloppyWatchlistType.SHOWS -> traktSource.postRating(network as Show, rating, ratedAt)
    }
  }

  private suspend fun deleteTraktRating(
    type: FloppyWatchlistType,
    network: Any,
  ) {
    when (type) {
      FloppyWatchlistType.MOVIES -> traktSource.deleteRating(network as Movie)
      FloppyWatchlistType.SHOWS -> traktSource.deleteRating(network as Show)
    }
  }

  private suspend fun updateLocalRating(
    type: FloppyWatchlistType,
    network: Any?,
    rating: Int?,
    changedAt: Long,
  ) {
    if (network == null) return
    val ratedAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(changedAt), ZoneOffset.UTC)
    when (type) {
      FloppyWatchlistType.MOVIES -> {
        val movie = mappers.movie.fromNetwork(network as Movie)
        localSource.movies.upsert(listOf(mappers.movie.toDatabase(movie)))
        if (rating == null) {
          localSource.ratings.deleteByType(movie.traktId, "movie")
        } else {
          localSource.ratings.replace(mappers.userRatings.toDatabaseMovie(movie, rating, ratedAt))
        }
      }
      FloppyWatchlistType.SHOWS -> {
        val show = mappers.show.fromNetwork(network as Show)
        localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))
        if (rating == null) {
          localSource.ratings.deleteByType(show.traktId, "show")
        } else {
          localSource.ratings.replace(mappers.userRatings.toDatabaseShow(show, rating, ratedAt))
        }
      }
    }
  }

  private fun RatingResultMovie.toBridgeRating(): TraktBridgeRating? {
    val tmdbId = movie.ids.tmdb ?: return null
    val traktId = movie.ids.trakt ?: return null
    return TraktBridgeRating(tmdbId, traktId, rating, rated_at.toEpochMillis() ?: 0)
  }

  private fun RatingResultShow.toBridgeRating(): TraktBridgeRating? {
    val tmdbId = show.ids.tmdb ?: return null
    val traktId = show.ids.trakt ?: return null
    return TraktBridgeRating(tmdbId, traktId, rating, rated_at.toEpochMillis() ?: 0)
  }

  private fun Double?.toTraktRating(): Int? =
    this
      ?.takeIf { it.isFinite() }
      ?.roundToInt()
      ?.coerceIn(1, 10)

  private fun String?.toEpochMillis(): Long? = toUtcDateTime()?.toInstant()?.toEpochMilli()

  private fun Long.safeTimestamp(): Long = takeIf { it > 0 } ?: nowUtcMillis()

  private data class TraktBridgeRating(
    val tmdbId: Long,
    val traktId: Long,
    val rating: Int,
    val changedAt: Long,
  )
}
