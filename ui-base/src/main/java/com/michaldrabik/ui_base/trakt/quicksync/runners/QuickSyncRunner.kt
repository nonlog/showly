package com.michaldrabik.ui_base.trakt.quicksync.runners

import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.TraktSyncQueue
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type.EPISODE
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type.HIDDEN_MOVIE
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type.HIDDEN_SHOW
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type.MOVIE
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type.MOVIE_WATCHLIST
import com.michaldrabik.data_local.database.model.TraktSyncQueue.Type.SHOW_WATCHLIST
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.SyncExportItem
import com.michaldrabik.data_remote.trakt.model.SyncExportRequest
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.trakt.TraktSyncRunner
import com.michaldrabik.ui_base.trakt.quicksync.runners.cases.QuickSyncDuplicateEpisodesCase
import com.michaldrabik.ui_base.trakt.quicksync.runners.cases.QuickSyncDuplicateMoviesCase
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickSyncRunner @Inject constructor(
  private val remoteSource: AuthorizedTraktRemoteDataSource,
  private val localSource: LocalDataSource,
  private val settingsRepository: SettingsRepository,
  private val duplicateEpisodesCase: QuickSyncDuplicateEpisodesCase,
  private val duplicateMoviesCase: QuickSyncDuplicateMoviesCase,
  userTraktManager: UserTraktManager,
) : TraktSyncRunner(userTraktManager) {

  companion object {
    private const val BATCH_LIMIT = 100
    private const val DELAY = 2000L
  }

  private val syncTypes = listOf(
    MOVIE,
    EPISODE,
    MOVIE_WATCHLIST,
    SHOW_WATCHLIST,
    HIDDEN_SHOW,
    HIDDEN_MOVIE,
  ).map { it.slug }

  override suspend fun run(): Int {
    Timber.d("Initialized.")
    checkAuthorization()
    val moviesEnabled = settingsRepository.isMoviesEnabled

    val historyCount = exportHistoryItems(moviesEnabled)
    val watchlistCount = exportWatchlistItems(moviesEnabled)
    val hiddenCount = exportHiddenItems(moviesEnabled)

    Timber.d("Finished with success.")
    return historyCount + watchlistCount + hiddenCount
  }

  private suspend fun exportHistoryItems(
    moviesEnabled: Boolean,
    remoteFetchedShows: List<SyncItem> = emptyList(),
    remoteFetchedMovies: List<SyncItem> = emptyList(),
    count: Int = 0,
    clearedProgressIds: MutableSet<Long> = mutableSetOf(),
  ): Int {
    val types = if (moviesEnabled) listOf(MOVIE, EPISODE) else listOf(EPISODE)
    val items = localSource.traktSyncQueue.getAllPendingTrakt(types.map { it.slug })
    if (items.isEmpty()) {
      Timber.d("Nothing to export. Cancelling..")
      return count
    }

    Timber.d("Exporting ${items.size} items...")

    val batch = items.take(BATCH_LIMIT)
    val effective = batch
      .groupBy { it.type to it.idTrakt }
      .values
      .map { entries -> entries.maxBy { it.updatedAt } }
    val addEpisodes = effective
      .filter { it.type == EPISODE.slug && it.operation != TraktSyncQueue.Operation.REMOVE.slug }
    val addMovies = effective
      .filter { it.type == MOVIE.slug && it.operation != TraktSyncQueue.Operation.REMOVE.slug }
    val removeEpisodes = effective
      .filter { it.type == EPISODE.slug && it.operation == TraktSyncQueue.Operation.REMOVE.slug }
    val removeMovies = effective
      .filter { it.type == MOVIE.slug && it.operation == TraktSyncQueue.Operation.REMOVE.slug }
    val clearProgress = addEpisodes.any { it.operation == TraktSyncQueue.Operation.ADD_WITH_CLEAR.slug }

    if (clearProgress) {
      Timber.d("Clearing progress for shows...")
      val requestItems = addEpisodes
        .mapNotNull { it.idList?.let { id -> SyncExportItem.create(id) } }
        .distinctBy { it.ids.trakt }
        .filterNot { clearedProgressIds.contains(it.ids.trakt) }
      if (requestItems.isNotEmpty()) {
        remoteSource.postDeleteProgress(SyncExportRequest(shows = requestItems))
        clearedProgressIds.addAll(requestItems.map { it.ids.trakt })
        delay(DELAY)
      }
    }

    val (duplicateEpisodes, remoteShows) =
      duplicateEpisodesCase.checkDuplicateEpisodes(addEpisodes, remoteFetchedShows)
    val (duplicateMovies, remoteMovies) =
      duplicateMoviesCase.checkDuplicateMovies(addMovies, remoteFetchedMovies)

    val addRequest = SyncExportRequest(
      episodes = addEpisodes
        .map { SyncExportItem.create(it.idTrakt, dateIsoStringFromMillis(it.updatedAt)) }
        .filter { it.ids.trakt !in duplicateEpisodes },
      movies = addMovies
        .map { SyncExportItem.create(it.idTrakt, dateIsoStringFromMillis(it.updatedAt)) }
        .filter { it.ids.trakt !in duplicateMovies },
    )
    val removeRequest = SyncExportRequest(
      episodes = removeEpisodes.map { SyncExportItem.create(it.idTrakt) },
      movies = removeMovies.map { SyncExportItem.create(it.idTrakt) },
    )

    if (addRequest.episodes.isNotEmpty() || addRequest.movies.isNotEmpty()) {
      remoteSource.postSyncWatched(addRequest)
      localSource.episodes.updateIsExported(
        episodesIds = addRequest.episodes.map { it.ids.trakt },
        exportedAt = nowUtcMillis(),
      )
    }
    if (removeRequest.episodes.isNotEmpty() || removeRequest.movies.isNotEmpty()) {
      remoteSource.postDeleteProgress(removeRequest)
    }

    // Acknowledge only the exact rows Trakt has accepted. Floppy independently
    // acknowledges the same durable rows before completed mutations are deleted.
    localSource.traktSyncQueue.markTraktDone(batch.map { it.id })

    val currentCount = count + effective.size
    val newItems = localSource.traktSyncQueue.getAllPendingTrakt(types.map { it.slug })
    if (newItems.isNotEmpty()) {
      delay(DELAY)
      return exportHistoryItems(
        moviesEnabled = moviesEnabled,
        remoteFetchedShows = remoteShows,
        remoteFetchedMovies = remoteMovies,
        count = currentCount,
        clearedProgressIds = clearedProgressIds.toMutableSet(),
      )
    }

    return currentCount
  }

  private suspend fun exportWatchlistItems(
    moviesEnabled: Boolean,
    count: Int = 0,
  ): Int {
    val types = if (moviesEnabled) listOf(MOVIE_WATCHLIST, SHOW_WATCHLIST) else listOf(SHOW_WATCHLIST)
    val items = localSource.traktSyncQueue.getAllPendingTrakt(types.map { it.slug }).take(BATCH_LIMIT)
    if (items.isEmpty()) {
      Timber.d("Nothing to export. Cancelling..")
      return count
    }

    Timber.d("Exporting watchlist items...")
    val effective = items
      .groupBy { it.type to it.idTrakt }
      .values
      .map { entries -> entries.maxBy { it.updatedAt } }
    val adds = effective.filter { it.operation != TraktSyncQueue.Operation.REMOVE.slug }
    val removes = effective.filter { it.operation == TraktSyncQueue.Operation.REMOVE.slug }

    fun List<TraktSyncQueue>.toRequest() =
      SyncExportRequest(
        shows = filter { it.type == SHOW_WATCHLIST.slug }
          .map { SyncExportItem.create(it.idTrakt, dateIsoStringFromMillis(it.updatedAt)) },
        movies = filter { it.type == MOVIE_WATCHLIST.slug }
          .map { SyncExportItem.create(it.idTrakt, dateIsoStringFromMillis(it.updatedAt)) },
      )

    val addRequest = adds.toRequest()
    val removeRequest = removes.toRequest()
    if (addRequest.shows.isNotEmpty() || addRequest.movies.isNotEmpty()) {
      remoteSource.postSyncWatchlist(addRequest)
    }
    if (removeRequest.shows.isNotEmpty() || removeRequest.movies.isNotEmpty()) {
      remoteSource.postDeleteWatchlist(removeRequest)
    }

    localSource.traktSyncQueue.markTraktDone(items.map { it.id })
    val currentCount = count + effective.size
    val newItems = localSource.traktSyncQueue.getAllPendingTrakt(types.map { it.slug })
    if (newItems.isNotEmpty()) {
      delay(DELAY)
      return exportWatchlistItems(moviesEnabled, currentCount)
    }

    return currentCount
  }

  private suspend fun exportHiddenItems(
    moviesEnabled: Boolean,
    count: Int = 0,
  ): Int {
    val types = if (moviesEnabled) listOf(HIDDEN_SHOW, HIDDEN_MOVIE) else listOf(HIDDEN_SHOW)
    val items = localSource.traktSyncQueue.getAll(types.map { it.slug }).take(BATCH_LIMIT)
    if (items.isEmpty()) {
      Timber.d("Nothing to export. Cancelling..")
      return count
    }

    Timber.d("Exporting hidden items...")

    val exportShows = items.filter { it.type == HIDDEN_SHOW.slug }.distinctBy { it.idTrakt }
    val exportMovies = items.filter { it.type == HIDDEN_MOVIE.slug }.distinctBy { it.idTrakt }

    if (exportShows.isNotEmpty()) {
      remoteSource.postHiddenShows(
        shows = exportShows.map { SyncExportItem.create(it.idTrakt, hiddenAt = dateIsoStringFromMillis(it.updatedAt)) },
      )
      delay(1500)
    }

    if (exportMovies.isNotEmpty()) {
      remoteSource.postHiddenMovies(
        movies = exportMovies.map {
          SyncExportItem.create(
            it.idTrakt,
            hiddenAt = dateIsoStringFromMillis(it.updatedAt),
          )
        },
      )
    }

    localSource.traktSyncQueue.delete(items)

    val currentCount = count + exportShows.count() + exportMovies.count()

    // Check for more items
    val newItems = localSource.traktSyncQueue.getAll(types.map { it.slug })
    if (newItems.isNotEmpty()) {
      delay(DELAY)
      return exportHiddenItems(moviesEnabled, currentCount)
    }

    return currentCount
  }
}
