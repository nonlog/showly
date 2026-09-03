package com.michaldrabik.ui_base.floppy

import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyHistoryType
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.SyncHistoryItem
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyHistorySyncRunner @Inject constructor(
  private val traktSource: AuthorizedTraktRemoteDataSource,
  private val floppySource: FloppyRemoteDataSource,
) {
  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }
    return syncMovies() + syncEpisodes()
  }

  private suspend fun syncMovies(): Int {
    val type = FloppyHistoryType.MOVIES
    val checkpoint = floppySource.getHistoryCheckpoint(type)
    val history = traktSource.fetchSyncHistory("movies", checkpoint).sortedBy(SyncHistoryItem::id)
    var writes = 0
    history.forEach { item ->
      val tmdbId = item.movie?.ids?.tmdb
      val watchedAt = item.watched_at
      if (tmdbId == null || watchedAt.isNullOrBlank()) {
        Timber.w("Skipping Trakt movie history " + item.id + ": missing TMDB id or watched_at")
      } else if (floppySource.ensureMovieHistory(tmdbId, watchedAt)) {
        writes += 1
      }
      floppySource.setHistoryCheckpoint(type, item.id)
    }
    return writes
  }

  private suspend fun syncEpisodes(): Int {
    val type = FloppyHistoryType.EPISODES
    val checkpoint = floppySource.getHistoryCheckpoint(type)
    val history = traktSource.fetchSyncHistory("episodes", checkpoint).sortedBy(SyncHistoryItem::id)
    var writes = 0
    history.forEach { item ->
      val showTmdbId = item.show?.ids?.tmdb
      val season = item.episode?.season
      val episode = item.episode?.number
      val watchedAt = item.watched_at
      if (showTmdbId == null || season == null || episode == null || watchedAt.isNullOrBlank()) {
        Timber.w("Skipping Trakt episode history " + item.id + ": missing media identity or watched_at")
      } else if (floppySource.ensureEpisodeHistory(showTmdbId, season, episode, watchedAt)) {
        writes += 1
      }
      floppySource.setHistoryCheckpoint(type, item.id)
    }
    return writes
  }
}
