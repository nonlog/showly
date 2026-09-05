package com.michaldrabik.ui_base.floppy

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.TraktSyncQueue
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Operation
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type
import com.michaldrabik.data_remote.floppy.FloppyBridgeHistoryIdentity
import com.michaldrabik.data_remote.floppy.FloppyBridgeHistoryKind
import com.michaldrabik.data_remote.floppy.FloppyBridgeRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyWatchlistType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast path for mutations that originate in Showly itself.
 *
 * The full Trakt <-> Floppy bridge is intentionally expensive because it must discover
 * mutations from both remote providers. A local mutation already has a trustworthy clock
 * and identity, so it can be sent directly to Floppy before the existing Trakt QuickSync
 * drains the same durable queue.
 */
@Singleton
class FloppyQuickSyncRunner @Inject constructor(
  private val localSource: LocalDataSource,
  private val floppySource: FloppyRemoteDataSource,
  private val floppyBridgeSource: FloppyBridgeRemoteDataSource,
) {

  private val supportedTypes = listOf(
    Type.MOVIE,
    Type.EPISODE,
    Type.MOVIE_WATCHLIST,
    Type.SHOW_WATCHLIST,
  ).map(Type::slug)

  suspend fun run(): Int {
    val queued = localSource.traktSyncQueue.getAllPendingFloppy(supportedTypes)
    if (queued.isEmpty()) return 0

    if (!floppySource.getConfig().enabled) {
      localSource.traktSyncQueue.markFloppyDone(queued.map { it.id })
      return 0
    }

    // Collapse rapid toggles for the same local entity. The newest local mutation is
    // authoritative; once it succeeds, every older pending mutation in that group is
    // acknowledged because it has been superseded by the final state.
    val groups = queued
      .groupBy { it.type to it.idTrakt }
      .values
      .sortedBy { items -> items.maxOf { it.updatedAt } }

    var changes = 0
    groups.forEach { items ->
      val effective = items.maxBy { it.updatedAt }
      changes += when (effective.type) {
        Type.MOVIE.slug -> syncMovieHistory(effective)
        Type.EPISODE.slug -> syncEpisodeHistory(effective)
        Type.MOVIE_WATCHLIST.slug -> syncWatchlist(effective, FloppyWatchlistType.MOVIES)
        Type.SHOW_WATCHLIST.slug -> syncWatchlist(effective, FloppyWatchlistType.SHOWS)
        else -> 0
      }
      localSource.traktSyncQueue.markFloppyDone(items.map { it.id })
    }

    Timber.d("Showly -> Floppy QuickSync completed with $changes change(s)")
    return changes
  }

  private suspend fun syncMovieHistory(item: TraktSyncQueue): Int {
    val movie = localSource.movies.getById(item.idTrakt)
    val tmdbId = movie?.idTmdb?.takeIf { it > 0 } ?: missingIdentity(item)

    return when (item.operation) {
      Operation.REMOVE.slug -> {
        val identity = FloppyBridgeHistoryIdentity(FloppyBridgeHistoryKind.MOVIE, tmdbId)
        floppyBridgeSource.fetchHistoryEvents(identity).sumOf { event ->
          if (floppyBridgeSource.removeHistoryEvent(event)) 1 else 0
        }
      }
      else -> if (floppySource.ensureMovieHistory(tmdbId, dateIsoStringFromMillis(item.updatedAt))) 1 else 0
    }
  }

  private suspend fun syncEpisodeHistory(item: TraktSyncQueue): Int {
    val episode = localSource.episodes.getAll(listOf(item.idTrakt)).firstOrNull()
    // Persisted coordinates are authoritative for REMOVE because an unfollowed
    // episode is deleted from Showly immediately when the user marks it unwatched.
    // Fall back to the local row for pre-schema-44/add paths.
    val showTmdbId = item.mediaTmdbId
      ?.takeIf { it > 0 }
      ?: episode
        ?.let { localSource.shows.getById(it.idShowTrakt) }
        ?.idTmdb
        ?.takeIf { it > 0 }
      ?: missingIdentity(item)
    val seasonNumber = item.seasonNumber ?: episode?.seasonNumber ?: missingIdentity(item)
    val episodeNumber = item.episodeNumber ?: episode?.episodeNumber ?: missingIdentity(item)

    return when (item.operation) {
      Operation.REMOVE.slug -> {
        val identity = FloppyBridgeHistoryIdentity(
          kind = FloppyBridgeHistoryKind.EPISODE,
          tmdbId = showTmdbId,
          season = seasonNumber,
          episode = episodeNumber,
        )
        floppyBridgeSource.fetchHistoryEvents(identity).sumOf { event ->
          if (floppyBridgeSource.removeHistoryEvent(event)) 1 else 0
        }
      }
      else -> if (
        floppySource.ensureEpisodeHistory(
          showTmdbId = showTmdbId,
          season = seasonNumber,
          episode = episodeNumber,
          watchedAt = dateIsoStringFromMillis(item.updatedAt),
        )
      ) {
        1
      } else {
        0
      }
    }
  }

  private suspend fun syncWatchlist(
    item: TraktSyncQueue,
    type: FloppyWatchlistType,
  ): Int {
    val tmdbId = when (type) {
      FloppyWatchlistType.MOVIES -> localSource.movies.getById(item.idTrakt)?.idTmdb
      FloppyWatchlistType.SHOWS -> localSource.shows.getById(item.idTrakt)?.idTmdb
    }?.takeIf { it > 0 } ?: missingIdentity(item)

    return when (item.operation) {
      Operation.REMOVE.slug -> {
        val matches = floppyBridgeSource.fetchWatchlist(type).filter { it.tmdbId == tmdbId }
        matches.sumOf { tracked ->
          if (floppyBridgeSource.removePlanning(type, tmdbId, tracked.consumptionId)) 1 else 0
        }
      }
      else -> if (floppyBridgeSource.ensureWatchlistPresent(type, tmdbId)) 1 else 0
    }
  }

  private fun missingIdentity(item: TraktSyncQueue): Nothing {
    error("Showly -> Floppy QuickSync is missing TMDB identity for ${item.type}#${item.idTrakt}")
  }
}
