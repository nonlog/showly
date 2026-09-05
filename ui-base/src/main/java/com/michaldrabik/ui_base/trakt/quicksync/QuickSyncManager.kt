package com.michaldrabik.ui_base.trakt.quicksync

import androidx.work.WorkManager
import com.michaldrabik.common.Mode
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.TraktSyncQueue
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Operation
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import timber.log.Timber
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickSyncManager @Inject constructor(
  private val userTraktManager: UserTraktManager,
  private val settingsRepository: SettingsRepository,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val workManager: WorkManager,
  private val floppyRemoteDataSource: FloppyRemoteDataSource,
) {

  suspend fun scheduleEpisodes(
    episodesIds: List<Long>,
    showId: Long,
    customDate: ZonedDateTime?,
    clearProgress: Boolean = false,
  ) {
    if (!ensureBridgeMutationSync() && !(clearProgress && ensureBridgeMutationRemove())) {
      return
    }

    val timestamp = customDate?.toUtcZone()?.toMillis() ?: nowUtcMillis()
    val showTmdbId = localSource.shows
      .getById(showId)
      ?.idTmdb
      ?.takeIf { it > 0 }
    val episodesById = localSource.episodes.getAll(episodesIds).associateBy { it.idTrakt }
    val items = episodesIds.map { episodeId ->
      val episode = episodesById[episodeId]
      TraktSyncQueue
        .createEpisode(episodeId, showId, timestamp, timestamp, clearProgress)
        .copy(
          mediaTmdbId = showTmdbId,
          seasonNumber = episode?.seasonNumber,
          episodeNumber = episode?.episodeNumber,
        )
    }
    localSource.traktSyncQueue.insert(items)
    Timber.d("Episodes added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleMovies(
    moviesIds: List<Long>,
    customDate: ZonedDateTime?,
  ) {
    if (!ensureBridgeMutationSync()) return

    val timestamp = customDate?.toUtcZone()?.toMillis() ?: nowUtcMillis()
    val items = moviesIds.map { TraktSyncQueue.createMovie(it, timestamp, timestamp) }
    localSource.traktSyncQueue.insert(items)
    Timber.d("Movies added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleShowsWatchlist(showsIds: List<Long>) {
    if (!ensureBridgeMutationSync()) return

    val time = nowUtcMillis()
    val items = showsIds.map { TraktSyncQueue.createShowWatchlist(it, time, time) }
    localSource.traktSyncQueue.insert(items)
    Timber.d("Shows added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleMoviesWatchlist(moviesIds: List<Long>) {
    if (!ensureBridgeMutationSync()) return

    val time = nowUtcMillis()
    val items = moviesIds.map { TraktSyncQueue.createMovieWatchlist(it, time, time) }
    localSource.traktSyncQueue.insert(items)
    Timber.d("Movies added into sync queue. Count: ${items.size}")

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleAddToList(
    idTrakt: Long,
    idList: Long,
    type: Mode,
  ) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val item = when (type) {
      Mode.SHOWS -> TraktSyncQueue.createListShow(idTrakt, idList, Operation.ADD, time, time)
      Mode.MOVIES -> TraktSyncQueue.createListMovie(idTrakt, idList, Operation.ADD, time, time)
    }

    val itemType = when (type) {
      Mode.SHOWS -> Type.LIST_ITEM_SHOW
      Mode.MOVIES -> Type.LIST_ITEM_MOVIE
    }

    transactions.withTransaction {
      localSource.traktSyncQueue.delete(idTrakt, idList, itemType.slug, Operation.ADD.slug)
      val count = localSource.traktSyncQueue.delete(idTrakt, idList, itemType.slug, Operation.REMOVE.slug)
      if (count == 0) {
        localSource.traktSyncQueue.insert(listOf(item))
        Timber.d("Added ${type.type} list item into add to list queue.")
      }
    }

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleRemoveFromList(
    idTrakt: Long,
    idList: Long,
    type: Mode,
  ) {
    if (!ensureQuickRemove()) return

    val time = nowUtcMillis()
    val item = when (type) {
      Mode.SHOWS -> TraktSyncQueue.createListShow(idTrakt, idList, Operation.REMOVE, time, time)
      Mode.MOVIES -> TraktSyncQueue.createListMovie(idTrakt, idList, Operation.REMOVE, time, time)
    }

    val itemType = when (type) {
      Mode.SHOWS -> Type.LIST_ITEM_SHOW
      Mode.MOVIES -> Type.LIST_ITEM_MOVIE
    }

    transactions.withTransaction {
      localSource.traktSyncQueue.delete(idTrakt, idList, itemType.slug, Operation.REMOVE.slug)
      val count = localSource.traktSyncQueue.delete(idTrakt, idList, itemType.slug, Operation.ADD.slug)
      if (count == 0 && ensureQuickRemove()) {
        localSource.traktSyncQueue.insert(listOf(item))
        Timber.d("Added ${type.type} list item into remove from list queue.")
      }
    }

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun scheduleHidden(
    idTrakt: Long,
    type: Mode,
    operation: Operation,
  ) {
    if (!ensureQuickSync()) return

    val time = nowUtcMillis()
    val item = when (type) {
      Mode.SHOWS -> TraktSyncQueue.createHiddenShow(idTrakt, operation, time, time)
      Mode.MOVIES -> TraktSyncQueue.createHiddenMovie(idTrakt, operation, time, time)
    }

    localSource.traktSyncQueue.insert(listOf(item))

    when (type) {
      Mode.SHOWS -> Timber.d("Hidden show added into sync queue. #$idTrakt")
      Mode.MOVIES -> Timber.d("Hidden movie added into sync queue. #$idTrakt")
    }

    QuickSyncWorker.schedule(workManager)
  }

  suspend fun clearEpisodes(episodesIds: List<Long>) {
    if (!ensureBridgeMutationRemove()) return

    if (!floppyRemoteDataSource.getConfig().enabled) {
      val count = localSource.traktSyncQueue.deleteAll(episodesIds, Type.EPISODE.slug)
      Timber.d("Episodes removed from sync queue. Count: $count")
      return
    }

    val time = nowUtcMillis()
    val episodesById = localSource.episodes.getAll(episodesIds).associateBy { it.idTrakt }
    val showTmdbByTrakt = episodesById.values
      .map { it.idShowTrakt }
      .distinct()
      .associateWith { showTraktId ->
        localSource.shows
          .getById(showTraktId)
          ?.idTmdb
          ?.takeIf { it > 0 }
      }
    val removals = episodesIds.map { id ->
      val episode = episodesById[id]
      TraktSyncQueue
        .createEpisode(id, episode?.idShowTrakt, time, time, clearProgress = false)
        .copy(
          operation = Operation.REMOVE.slug,
          mediaTmdbId = episode?.idShowTrakt?.let(showTmdbByTrakt::get),
          seasonNumber = episode?.seasonNumber,
          episodeNumber = episode?.episodeNumber,
        )
    }
    transactions.withTransaction {
      localSource.traktSyncQueue.deleteAll(episodesIds, Type.EPISODE.slug)
      localSource.traktSyncQueue.insert(removals)
    }
    QuickSyncWorker.schedule(workManager)
  }

  suspend fun clearEpisodes() {
    if (!ensureQuickRemove()) return

    localSource.traktSyncQueue.deleteAll(Type.EPISODE.slug)
    Timber.d("Episodes removed from sync queue.")
  }

  suspend fun clearMovies(moviesIds: List<Long>) {
    if (!ensureBridgeMutationRemove()) return

    if (!floppyRemoteDataSource.getConfig().enabled) {
      localSource.traktSyncQueue.deleteAll(moviesIds, Type.MOVIE.slug)
      Timber.d("Movies removed from sync queue. Count: ${moviesIds.size}")
      return
    }

    val time = nowUtcMillis()
    val removals = moviesIds.map { id ->
      TraktSyncQueue.createMovie(id, time, time).copy(operation = Operation.REMOVE.slug)
    }
    transactions.withTransaction {
      localSource.traktSyncQueue.deleteAll(moviesIds, Type.MOVIE.slug)
      localSource.traktSyncQueue.insert(removals)
    }
    QuickSyncWorker.schedule(workManager)
  }

  suspend fun clearWatchlistShows(showsIds: List<Long>) {
    if (!ensureBridgeMutationRemove()) return

    if (!floppyRemoteDataSource.getConfig().enabled) {
      localSource.traktSyncQueue.deleteAll(showsIds, Type.SHOW_WATCHLIST.slug)
      Timber.d("Shows removed from sync queue. Count: ${showsIds.size}")
      return
    }

    val time = nowUtcMillis()
    val removals = showsIds.map { id ->
      TraktSyncQueue.createShowWatchlist(id, time, time).copy(operation = Operation.REMOVE.slug)
    }
    transactions.withTransaction {
      localSource.traktSyncQueue.deleteAll(showsIds, Type.SHOW_WATCHLIST.slug)
      localSource.traktSyncQueue.insert(removals)
    }
    QuickSyncWorker.schedule(workManager)
  }

  suspend fun clearWatchlistMovies(moviesIds: List<Long>) {
    if (!ensureBridgeMutationRemove()) return

    if (!floppyRemoteDataSource.getConfig().enabled) {
      localSource.traktSyncQueue.deleteAll(moviesIds, Type.MOVIE_WATCHLIST.slug)
      Timber.d("Movies removed from sync queue. Count: ${moviesIds.size}")
      return
    }

    val time = nowUtcMillis()
    val removals = moviesIds.map { id ->
      TraktSyncQueue.createMovieWatchlist(id, time, time).copy(operation = Operation.REMOVE.slug)
    }
    transactions.withTransaction {
      localSource.traktSyncQueue.deleteAll(moviesIds, Type.MOVIE_WATCHLIST.slug)
      localSource.traktSyncQueue.insert(removals)
    }
    QuickSyncWorker.schedule(workManager)
  }

  suspend fun clearHiddenShows(ids: List<Long>) {
    if (!ensureQuickRemove()) return

    localSource.traktSyncQueue.deleteAll(ids, Type.HIDDEN_SHOW.slug)
    Timber.d("Hidden shows removed from sync queue. Count: ${ids.size}")
  }

  suspend fun clearHiddenMovies(ids: List<Long>) {
    if (!ensureQuickRemove()) return

    localSource.traktSyncQueue.deleteAll(ids, Type.HIDDEN_MOVIE.slug)
    Timber.d("Hidden shows removed from sync queue. Count: ${ids.size}")
  }

  suspend fun isAnyScheduled(): Boolean {
    val hasTarget = userTraktManager.isAuthorized() || floppyRemoteDataSource.getConfig().enabled
    return hasTarget && localSource.traktSyncQueue.getAll().isNotEmpty()
  }

  private suspend fun ensureQuickSync(): Boolean {
    if (!ensureAuthorized()) return false

    val settings = settingsRepository.load()
    if (!settings.traktQuickSyncEnabled) {
      Timber.d("Quick Sync is disabled. Skipping...")
      return false
    }
    return true
  }

  private suspend fun ensureQuickRemove(): Boolean {
    if (!ensureAuthorized()) return false

    val settings = settingsRepository.load()
    if (!settings.traktQuickRemoveEnabled) {
      Timber.d("Quick Remove is disabled. Skipping...")
      return false
    }
    return true
  }

  private suspend fun ensureBridgeMutationSync(): Boolean {
    if (floppyRemoteDataSource.getConfig().enabled) return true
    return ensureQuickSync()
  }

  private suspend fun ensureBridgeMutationRemove(): Boolean {
    if (floppyRemoteDataSource.getConfig().enabled) return true
    return ensureQuickRemove()
  }

  private fun ensureAuthorized(): Boolean {
    if (!userTraktManager.isAuthorized()) {
      Timber.d("User not logged into Trakt. Skipping...")
      return false
    }
    return true
  }
}
