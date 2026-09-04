package com.michaldrabik.ui_base.floppy

import com.michaldrabik.common.Mode
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.CustomListItem
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyListItemRef
import com.michaldrabik.data_remote.floppy.FloppyListsRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyWatchlistType
import com.michaldrabik.repository.ListsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyListsSyncRunner @Inject constructor(
  private val listsRepository: ListsRepository,
  private val localSource: LocalDataSource,
  private val floppySource: FloppyRemoteDataSource,
  private val floppyListsSource: FloppyListsRemoteDataSource,
) {
  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }

    val localLists = listsRepository.loadAll()
    var changes = 0
    localLists.forEach { list ->
      if (
        floppyListsSource.ensureOwnedList(
          localListId = list.id,
          name = list.name,
          description = list.description,
          isPublic = list.privacy == "public",
        )
      ) {
        changes += 1
      }

      val currentItems = listsRepository
        .loadItemsById(list.id)
        .mapNotNull { resolveListItem(it) }
        .toSet()
      currentItems.forEach { item ->
        if (floppyListsSource.ensureListItem(list.id, item)) changes += 1
      }
    }

    val localListIds = localLists.map { it.id }.toSet()
    val removedListIds = floppyListsSource.getOwnedLocalListIds() - localListIds
    removedListIds.forEach { localListId ->
      if (floppyListsSource.releaseOwnedList(localListId)) changes += 1
    }

    Timber.d("Floppy custom-list sync completed with $changes change(s)")
    return changes
  }

  private suspend fun resolveListItem(item: CustomListItem): FloppyListItemRef? =
    when (item.type) {
      Mode.MOVIES.type ->
        localSource.movies
          .getById(item.idTrakt)
          ?.idTmdb
          ?.takeIf { it > 0 }
          ?.let { FloppyListItemRef(FloppyWatchlistType.MOVIES, it) }
      Mode.SHOWS.type ->
        localSource.shows
          .getById(item.idTrakt)
          ?.idTmdb
          ?.takeIf { it > 0 }
          ?.let { FloppyListItemRef(FloppyWatchlistType.SHOWS, it) }
      else -> null
    }
}
