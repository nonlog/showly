package com.michaldrabik.ui_base.floppy

import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyWatchlistType
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.repository.shows.ShowsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyWatchlistSyncRunner @Inject constructor(
  private val moviesRepository: MoviesRepository,
  private val showsRepository: ShowsRepository,
  private val floppySource: FloppyRemoteDataSource,
) {
  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }

    val movieTmdbIds = moviesRepository
      .watchlistMovies
      .loadAll()
      .mapNotNull { movie ->
        movie.ids.tmdb.id
          .takeIf { it > 0 }
      }.toSet()
    val showTmdbIds = showsRepository
      .watchlistShows
      .loadAll()
      .mapNotNull { show ->
        show.ids.tmdb.id
          .takeIf { it > 0 }
      }.toSet()

    return syncType(FloppyWatchlistType.MOVIES, movieTmdbIds) +
      syncType(FloppyWatchlistType.SHOWS, showTmdbIds)
  }

  private suspend fun syncType(
    type: FloppyWatchlistType,
    currentTmdbIds: Set<Long>,
  ): Int {
    var changes = 0
    currentTmdbIds.forEach { tmdbId ->
      if (floppySource.ensureWatchlistPlanning(type, tmdbId)) changes += 1
    }

    val removedTmdbIds = floppySource.getOwnedWatchlistTmdbIds(type) - currentTmdbIds
    removedTmdbIds.forEach { tmdbId ->
      if (floppySource.removeOwnedWatchlistPlanning(type, tmdbId)) changes += 1
    }

    Timber.d("Floppy watchlist sync for $type completed with $changes change(s)")
    return changes
  }
}
