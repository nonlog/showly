package com.michaldrabik.ui_base.floppy

import com.michaldrabik.common.Mode
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toUtcDateTime
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_local.database.model.CustomList
import com.michaldrabik.data_local.database.model.CustomListItem
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyBridgeList
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyListItemRef
import com.michaldrabik.data_remote.floppy.FloppyListsRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyWatchlistType
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.data_remote.trakt.model.Movie
import com.michaldrabik.data_remote.trakt.model.Show
import com.michaldrabik.data_remote.trakt.model.SyncItem
import com.michaldrabik.repository.bridge.BridgeConflictResolver
import com.michaldrabik.repository.bridge.BridgeObservedState
import com.michaldrabik.repository.bridge.BridgeSide
import com.michaldrabik.repository.bridge.BridgeSyncStateRepository
import com.michaldrabik.repository.mappers.Mappers
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import com.michaldrabik.data_remote.trakt.model.CustomList as TraktList

@Singleton
class FloppyBridgeListsRunner @Inject constructor(
  private val traktSource: AuthorizedTraktRemoteDataSource,
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val identityGuard: FloppyBridgeIdentityGuard,
  private val floppySource: FloppyRemoteDataSource,
  private val floppyListsSource: FloppyListsRemoteDataSource,
  private val bridgeStateRepository: BridgeSyncStateRepository,
) {

  companion object {
    private const val PRESENT = "present"
    private const val DOMAIN_PAIRS = "list_pairs"
    private const val DOMAIN_PRESENCE = "list_presence"
    private const val DOMAIN_METADATA = "list_metadata"
    private const val DOMAIN_MEMBERS_PREFIX = "list_members:"
  }

  suspend fun run(): Int {
    val config = floppySource.getConfig()
    if (!config.enabled) return 0
    val status = floppySource.validateConnection(config)
    check(status == FloppyConnectionStatus.CONNECTED) { "Floppy connection is not ready: $status" }
    identityGuard.ensureCurrent()

    val observedAt = nowUtcMillis()
    val activity = traktSource.fetchSyncActivity()
    val existingPairStates = bridgeStateRepository.getAll(DOMAIN_PAIRS)
    val pairedTraktIds = existingPairStates.mapNotNull { it.traktValue?.toLongOrNull() }.toSet()
    var traktLists = traktSource.fetchSyncLists()
    ensureTraktListsLocal(traktLists, pairedTraktIds)
    ensureLocalListsOnTrakt()
    traktLists = traktSource.fetchSyncLists()
    ensureTraktListsLocal(traktLists, pairedTraktIds)

    var localLists = localSource.customLists.getAll()
    var floppyLists = floppyListsSource.fetchLists()
    bootstrapPairs(localLists, traktLists, floppyLists)

    localLists = localSource.customLists.getAll()
    traktLists = traktSource.fetchSyncLists()
    floppyLists = floppyListsSource.fetchLists()

    val pairStates = bridgeStateRepository.getAll(DOMAIN_PAIRS).associateBy(BridgeSyncState::entityKey)
    val mappings = floppyListsSource.getOwnedListMappings()
    val localById = localLists.associateBy(CustomList::id).toMutableMap()
    val traktById = traktLists.associateBy { it.ids.trakt }.toMutableMap()
    val floppyById = floppyLists.associateBy(FloppyBridgeList::id).toMutableMap()
    var changes = 0

    mappings.toSortedMap().forEach { (localListId, mappedFloppyId) ->
      val pairKey = localListId.toString()
      val pairState = pairStates[pairKey]
      var local = localById[localListId]
      var traktId = local?.idTrakt ?: pairState?.traktValue?.toLongOrNull()
      var floppyId = mappedFloppyId
      var trakt = traktId?.let(traktById::get)
      var floppy = floppyById[floppyId]

      val previousPresence = bridgeStateRepository.get(DOMAIN_PRESENCE, pairKey)
      val traktPresence = BridgeConflictResolver.observe(
        previous = previousPresence.traktState(),
        currentValue = PRESENT.takeIf { trakt != null },
        remoteChangedAt = when {
          trakt != null && previousPresence?.traktObserved != true -> trakt.created_at.toEpochMillis()
          trakt == null && previousPresence?.traktValue != null -> activity.lists.updated_at.toEpochMillis()
          else -> null
        },
        observedAt = observedAt,
      )
      val floppyPresence = BridgeConflictResolver.observe(
        previous = previousPresence.floppyState(),
        currentValue = PRESENT.takeIf { floppy != null },
        remoteChangedAt = if (floppy != null && previousPresence?.floppyObserved != true) {
          floppy.latestUpdate.takeIf { it > 0 }
        } else {
          null
        },
        observedAt = observedAt,
      )
      val presenceResolution = BridgeConflictResolver.resolve(
        trakt = traktPresence,
        floppy = floppyPresence,
        previousResolvedValue = previousPresence?.resolvedValue,
        previousResolvedAt = previousPresence?.resolvedChangedAt ?: 0,
      )

      if (presenceResolution.value == null) {
        if (trakt != null) {
          traktSource.deleteList(trakt.ids.trakt)
          changes += 1
        }
        if (floppy != null) {
          if (floppyListsSource.deleteOwnedList(localListId)) changes += 1
        } else {
          floppyListsSource.releaseOwnedList(localListId)
        }
        if (local != null) localSource.customLists.deleteById(localListId)
        bridgeStateRepository.save(
          previousPresence.toState(
            domain = DOMAIN_PRESENCE,
            entityKey = pairKey,
            trakt = BridgeObservedState(null, presenceResolution.changedAt, true),
            floppy = BridgeObservedState(null, presenceResolution.changedAt, true),
            resolvedValue = null,
            resolvedAt = presenceResolution.changedAt,
          ),
        )
        return@forEach
      }

      val currentFloppy = floppy
      if (trakt == null && currentFloppy != null) {
        val createdTrakt = traktSource.postCreateList(
          name = currentFloppy.name,
          description = currentFloppy.description,
          privacy = currentFloppy.toTraktPrivacy(),
        )
        trakt = createdTrakt
        traktId = createdTrakt.ids.trakt
        val updatedLocal = upsertLocalFromTrakt(localListId, local, createdTrakt)
        local = updatedLocal
        localById[localListId] = updatedLocal
        traktById[createdTrakt.ids.trakt] = createdTrakt
        changes += 1
      }
      val currentLocal = local
      if (floppy == null && currentLocal != null) {
        floppyListsSource.ensureOwnedList(
          localListId = localListId,
          name = currentLocal.name,
          description = currentLocal.description,
          isPublic = currentLocal.privacy == "public",
        )
        floppyId = floppyListsSource.getOwnedListMappings()[localListId] ?: floppyId
        val createdFloppy = FloppyBridgeList(
          id = floppyId,
          name = currentLocal.name,
          description = currentLocal.description.orEmpty(),
          isPublic = currentLocal.privacy == "public",
          latestUpdate = presenceResolution.changedAt,
        )
        floppy = createdFloppy
        floppyById[floppyId] = createdFloppy
        changes += 1
      }
      val activeLocal = local
      val activeTrakt = trakt
      val activeFloppy = floppy
      if (activeLocal == null || activeTrakt == null || activeFloppy == null || traktId == null) {
        Timber.w("Unable to complete Trakt <-> Floppy list pair for local id $localListId")
        return@forEach
      }

      floppyListsSource.bindOwnedList(localListId, activeFloppy.id)
      savePair(localListId, activeTrakt.ids.trakt, activeFloppy.id)
      bridgeStateRepository.save(
        previousPresence.toState(
          domain = DOMAIN_PRESENCE,
          entityKey = pairKey,
          trakt = BridgeObservedState(PRESENT, presenceResolution.changedAt, true),
          floppy = BridgeObservedState(PRESENT, presenceResolution.changedAt, true),
          resolvedValue = PRESENT,
          resolvedAt = presenceResolution.changedAt,
        ),
      )

      val metadataResult = reconcileMetadata(
        local = activeLocal,
        trakt = activeTrakt,
        floppy = activeFloppy,
        observedAt = observedAt,
      )
      changes += metadataResult.changes
      localById[localListId] = metadataResult.local
      traktById[metadataResult.trakt.ids.trakt] = metadataResult.trakt
      floppyById[metadataResult.floppy.id] = metadataResult.floppy

      changes += reconcileMembers(
        localListId = localListId,
        trakt = metadataResult.trakt,
        floppy = metadataResult.floppy,
        traktDeletionClock = maxOf(
          metadataResult.trakt.updated_at.toEpochMillis() ?: 0,
          activity.lists.updated_at.toEpochMillis() ?: 0,
        ),
        observedAt = observedAt,
      )
    }

    Timber.d("Trakt <-> Floppy custom-list bridge completed with $changes change(s)")
    return changes
  }

  private suspend fun ensureTraktListsLocal(
    traktLists: List<TraktList>,
    pairedTraktIds: Set<Long>,
  ) {
    val localByTrakt = localSource.customLists
      .getAll()
      .mapNotNull { list -> list.idTrakt?.let { it to list } }
      .toMap()
    traktLists.forEach { remote ->
      if (remote.ids.trakt in localByTrakt || remote.ids.trakt in pairedTraktIds) return@forEach
      val local = mappers.customList.toDatabase(mappers.customList.fromNetwork(remote))
      localSource.customLists.insert(listOf(local))
    }
  }

  private suspend fun ensureLocalListsOnTrakt() {
    localSource.customLists
      .getAll()
      .filter { it.idTrakt == null }
      .forEach { local ->
        val created = traktSource.postCreateList(
          name = local.name,
          description = local.description,
          privacy = local.privacy,
        )
        localSource.customLists.updateTraktId(
          id = local.id,
          idTrakt = created.ids.trakt,
          idSlug = created.ids.slug,
          timestamp = nowUtcMillis(),
        )
      }
  }

  private suspend fun bootstrapPairs(
    localLists: List<CustomList>,
    traktLists: List<TraktList>,
    floppyLists: List<FloppyBridgeList>,
  ) {
    val traktById = traktLists.associateBy { it.ids.trakt }
    val mappings = floppyListsSource.getOwnedListMappings().toMutableMap()
    val usedFloppyIds = mappings.values.toMutableSet()

    localLists
      .filter { it.idTrakt != null && it.id !in mappings }
      .forEach { local ->
        val localTraktId = local.idTrakt ?: return@forEach
        val trakt = traktById[localTraktId] ?: return@forEach
        val candidates = floppyLists.filter { remote ->
          remote.id !in usedFloppyIds && remote.sharedMetadata() == trakt.sharedMetadata()
        }
        if (candidates.size == 1) {
          val remote = candidates.single()
          floppyListsSource.bindOwnedList(local.id, remote.id)
          usedFloppyIds += remote.id
        } else {
          floppyListsSource.ensureOwnedList(
            localListId = local.id,
            name = trakt.name,
            description = trakt.description,
            isPublic = trakt.privacy == "public",
          )
          floppyListsSource.getOwnedListMappings()[local.id]?.let(usedFloppyIds::add)
        }
      }

    floppyLists
      .filter { it.id !in usedFloppyIds }
      .forEach { remote ->
        val created = traktSource.postCreateList(
          name = remote.name,
          description = remote.description,
          privacy = remote.toTraktPrivacy(),
        )
        val local = mappers.customList.toDatabase(mappers.customList.fromNetwork(created))
        val localId = localSource.customLists.insert(listOf(local)).single()
        floppyListsSource.bindOwnedList(localId, remote.id)
        usedFloppyIds += remote.id
      }
  }

  private suspend fun reconcileMetadata(
    local: CustomList,
    trakt: TraktList,
    floppy: FloppyBridgeList,
    observedAt: Long,
  ): MetadataResult {
    val key = local.id.toString()
    val previous = bridgeStateRepository.get(DOMAIN_METADATA, key)
    val traktMetadata = trakt.sharedMetadata()
    val floppyMetadata = floppy.sharedMetadata()
    val traktState = BridgeConflictResolver.observe(
      previous = previous.traktState(),
      currentValue = traktMetadata.fingerprint(),
      remoteChangedAt = trakt.updated_at.toEpochMillis(),
      observedAt = observedAt,
    )
    val floppyFingerprint = floppyMetadata.fingerprint()
    val floppyState = BridgeConflictResolver.observe(
      previous = previous.floppyState(),
      currentValue = floppyFingerprint,
      remoteChangedAt = if (previous?.floppyObserved == true) {
        null
      } else {
        floppy.latestUpdate.takeIf { it > 0 } ?: 1
      },
      observedAt = observedAt,
    )
    val resolution = BridgeConflictResolver.resolve(
      trakt = traktState,
      floppy = floppyState,
      previousResolvedValue = previous?.resolvedValue,
      previousResolvedAt = previous?.resolvedChangedAt ?: 0,
    )

    var resolvedLocal = local
    var resolvedTrakt = trakt
    var resolvedFloppy = floppy
    var changes = 0
    when (resolution.winner) {
      BridgeSide.TRAKT -> if (traktMetadata != floppyMetadata) {
        floppyListsSource.ensureOwnedList(
          localListId = local.id,
          name = traktMetadata.name,
          description = traktMetadata.description,
          isPublic = traktMetadata.isPublic,
        )
        resolvedFloppy = floppy.copy(
          name = traktMetadata.name,
          description = traktMetadata.description,
          isPublic = traktMetadata.isPublic,
        )
        changes += 1
      }
      BridgeSide.FLOPPY -> if (traktMetadata != floppyMetadata) {
        val updated = traktSource.postUpdateList(
          trakt.copy(
            name = floppyMetadata.name,
            description = floppyMetadata.description,
            privacy = floppyMetadata.toTraktPrivacy(),
          ),
        )
        resolvedTrakt = updated
        resolvedLocal = local.copy(
          name = updated.name,
          description = updated.description,
          privacy = updated.privacy,
          updatedAt = updated.updated_at.toEpochMillis() ?: observedAt,
        )
        localSource.customLists.update(listOf(resolvedLocal))
        changes += 1
      }
      null -> Unit
    }

    val resolvedFingerprint = when (resolution.winner) {
      BridgeSide.FLOPPY -> floppyMetadata.fingerprint()
      else -> traktMetadata.fingerprint()
    }
    bridgeStateRepository.save(
      previous.toState(
        domain = DOMAIN_METADATA,
        entityKey = key,
        trakt = BridgeObservedState(resolvedFingerprint, resolution.changedAt, true),
        floppy = BridgeObservedState(resolvedFingerprint, resolution.changedAt, true),
        resolvedValue = resolvedFingerprint,
        resolvedAt = resolution.changedAt,
      ),
    )
    return MetadataResult(resolvedLocal, resolvedTrakt, resolvedFloppy, changes)
  }

  private suspend fun reconcileMembers(
    localListId: Long,
    trakt: TraktList,
    floppy: FloppyBridgeList,
    traktDeletionClock: Long,
    observedAt: Long,
  ): Int {
    val domain = "$DOMAIN_MEMBERS_PREFIX$localListId"
    val traktMembers = traktSource
      .fetchSyncListItems(trakt.ids.trakt, true)
      .mapNotNull { it.toBridgeListMember() }
      .associateBy { it.key }
    val floppyMembers = floppyListsSource
      .fetchListItems(floppy.id)
      .associateBy { it.key() }
    val previous = bridgeStateRepository.getAll(domain).associateBy(BridgeSyncState::entityKey)
    val keys = buildSet {
      addAll(traktMembers.keys)
      addAll(floppyMembers.keys)
      addAll(previous.keys)
    }

    val addMovies = linkedSetOf<Long>()
    val addShows = linkedSetOf<Long>()
    val removeMovies = linkedSetOf<Long>()
    val removeShows = linkedSetOf<Long>()
    val pendingStates = mutableListOf<BridgeSyncState>()
    val localActions = mutableListOf<suspend () -> Unit>()
    var changes = 0

    keys.sorted().forEach { key ->
      val prior = previous[key]
      val traktMember = traktMembers[key]
      val floppyMember = floppyMembers[key]
      val traktState = BridgeConflictResolver.observe(
        previous = prior.traktState(),
        currentValue = PRESENT.takeIf { traktMember != null },
        remoteChangedAt = when {
          traktMember != null -> traktMember.changedAt
          prior?.traktValue != null -> traktDeletionClock.takeIf { it > 0 }
          else -> null
        },
        observedAt = observedAt,
      )
      val floppyState = BridgeConflictResolver.observe(
        previous = prior.floppyState(),
        currentValue = PRESENT.takeIf { floppyMember != null },
        remoteChangedAt = if (floppyMember != null && prior?.floppyObserved != true) {
          floppy.latestUpdate.takeIf { it > 0 }
        } else {
          null
        },
        observedAt = observedAt,
      )
      val resolution = BridgeConflictResolver.resolve(
        trakt = traktState,
        floppy = floppyState,
        previousResolvedValue = prior?.resolvedValue,
        previousResolvedAt = prior?.resolvedChangedAt ?: 0,
      )
      val itemRef = traktMember?.item ?: floppyMember ?: key.toListItemRef() ?: return@forEach
      var lookup = traktMember?.lookup
      if (resolution.value == PRESENT && lookup == null) {
        lookup = resolveMember(itemRef)
      }
      var finalTrakt = traktState
      var finalFloppy = floppyState

      if (resolution.value == PRESENT) {
        if (traktMember == null && lookup != null) {
          when (itemRef.type) {
            FloppyWatchlistType.MOVIES -> addMovies += lookup.traktId
            FloppyWatchlistType.SHOWS -> addShows += lookup.traktId
          }
          finalTrakt = BridgeObservedState(PRESENT, resolution.changedAt, true)
          changes += 1
        }
        if (floppyMember == null) {
          if (floppyListsSource.ensureListItem(localListId, itemRef)) changes += 1
          finalFloppy = BridgeObservedState(PRESENT, resolution.changedAt, true)
        }
        if (lookup != null) {
          localActions += { ensureLocalMember(localListId, itemRef.type, lookup, resolution.changedAt) }
        }
      } else {
        if (traktMember != null) {
          when (itemRef.type) {
            FloppyWatchlistType.MOVIES -> removeMovies += traktMember.lookup.traktId
            FloppyWatchlistType.SHOWS -> removeShows += traktMember.lookup.traktId
          }
          finalTrakt = BridgeObservedState(null, resolution.changedAt, true)
          changes += 1
        }
        if (floppyMember != null) {
          if (floppyListsSource.removeListItem(localListId, itemRef)) changes += 1
          finalFloppy = BridgeObservedState(null, resolution.changedAt, true)
        }
        (lookup ?: resolveMember(itemRef))?.let { resolved ->
          localActions += { removeLocalMember(localListId, itemRef.type, resolved.traktId) }
        }
      }

      pendingStates += prior.toState(
        domain = domain,
        entityKey = key,
        trakt = finalTrakt,
        floppy = finalFloppy,
        resolvedValue = resolution.value,
        resolvedAt = resolution.changedAt,
      )
    }

    if (addMovies.isNotEmpty() || addShows.isNotEmpty()) {
      traktSource.postAddListItems(trakt.ids.trakt, addShows.toList(), addMovies.toList())
    }
    if (removeMovies.isNotEmpty() || removeShows.isNotEmpty()) {
      traktSource.postRemoveListItems(trakt.ids.trakt, removeShows.toList(), removeMovies.toList())
    }
    localActions.forEach { it() }
    pendingStates.forEach { bridgeStateRepository.save(it) }
    return changes
  }

  private suspend fun resolveMember(item: FloppyListItemRef): MemberLookup? {
    val local = when (item.type) {
      FloppyWatchlistType.MOVIES -> localSource.movies.getByTmdbId(item.tmdbId)?.let {
        MemberLookup(it.idTrakt)
      }
      FloppyWatchlistType.SHOWS -> localSource.shows.getByTmdbId(item.tmdbId)?.let {
        MemberLookup(it.idTrakt)
      }
    }
    if (local != null && local.traktId > 0) return local

    val search = remoteSource.trakt.fetchSearchId("tmdb", item.tmdbId.toString())
    return when (item.type) {
      FloppyWatchlistType.MOVIES -> search.firstNotNullOfOrNull { result ->
        result.movie
          ?.ids
          ?.trakt
          ?.takeIf { it > 0 }
          ?.let { MemberLookup(it, movie = result.movie) }
      }
      FloppyWatchlistType.SHOWS -> search.firstNotNullOfOrNull { result ->
        result.show
          ?.ids
          ?.trakt
          ?.takeIf { it > 0 }
          ?.let { MemberLookup(it, show = result.show) }
      }
    }
  }

  private suspend fun ensureLocalMember(
    localListId: Long,
    type: FloppyWatchlistType,
    lookup: MemberLookup,
    changedAt: Long,
  ) {
    when (type) {
      FloppyWatchlistType.MOVIES -> lookup.movie?.let { movie ->
        val mapped = mappers.movie.fromNetwork(movie)
        localSource.movies.upsert(listOf(mappers.movie.toDatabase(mapped)))
      }
      FloppyWatchlistType.SHOWS -> lookup.show?.let { show ->
        val mapped = mappers.show.fromNetwork(show)
        localSource.shows.upsert(listOf(mappers.show.toDatabase(mapped)))
      }
    }
    val typeName = type.localModeType()
    if (localSource.customListsItems.getByIdTrakt(localListId, lookup.traktId, typeName) != null) return
    val now = nowUtcMillis()
    localSource.customListsItems.insertItem(
      CustomListItem(
        idList = localListId,
        idTrakt = lookup.traktId,
        type = typeName,
        rank = 0,
        listedAt = changedAt.takeIf { it > 0 } ?: now,
        createdAt = now,
        updatedAt = now,
      ),
    )
    localSource.customLists.updateTimestamp(localListId, now)
  }

  private suspend fun removeLocalMember(
    localListId: Long,
    type: FloppyWatchlistType,
    traktId: Long,
  ) {
    val typeName = type.localModeType()
    if (localSource.customListsItems.getByIdTrakt(localListId, traktId, typeName) == null) return
    localSource.customListsItems.deleteItem(localListId, traktId, typeName)
    localSource.customLists.updateTimestamp(localListId, nowUtcMillis())
  }

  private suspend fun upsertLocalFromTrakt(
    localListId: Long,
    existing: CustomList?,
    trakt: TraktList,
  ): CustomList {
    val mapped = mappers.customList.toDatabase(mappers.customList.fromNetwork(trakt))
    val target = if (existing == null) {
      mapped.copy(id = localListId)
    } else {
      existing.copy(
        idTrakt = trakt.ids.trakt,
        idSlug = trakt.ids.slug,
        name = trakt.name,
        description = trakt.description,
        privacy = trakt.privacy,
        displayNumbers = trakt.display_numbers,
        allowComments = trakt.allow_comments,
        sortBy = trakt.sort_by,
        sortHow = trakt.sort_how,
        itemCount = trakt.item_count,
        commentCount = trakt.comment_count,
        likes = trakt.likes,
        createdAt = trakt.created_at.toEpochMillis() ?: existing.createdAt,
        updatedAt = trakt.updated_at.toEpochMillis() ?: nowUtcMillis(),
      )
    }
    if (existing == null) {
      localSource.customLists.insert(listOf(target))
    } else {
      localSource.customLists.update(listOf(target))
    }
    return target
  }

  private suspend fun savePair(
    localListId: Long,
    traktId: Long,
    floppyId: Long,
  ) {
    bridgeStateRepository.save(
      BridgeSyncState(
        domain = DOMAIN_PAIRS,
        entityKey = localListId.toString(),
        traktValue = traktId.toString(),
        traktObserved = true,
        floppyValue = floppyId.toString(),
        floppyObserved = true,
        resolvedValue = "$traktId:$floppyId",
      ),
    )
  }

  private fun SyncItem.toBridgeListMember(): BridgeListMember? {
    val movieValue = movie
    val movieIds = movieValue?.ids
    if (movieValue != null && movieIds != null) {
      val tmdbId = movieIds.tmdb
      val traktId = movieIds.trakt
      if (tmdbId != null && traktId != null) {
        return BridgeListMember(
          item = FloppyListItemRef(FloppyWatchlistType.MOVIES, tmdbId),
          lookup = MemberLookup(traktId, movie = movieValue),
          changedAt = listed_at.toEpochMillis() ?: 0,
        )
      }
    }
    val showValue = show
    val showIds = showValue?.ids
    if (showValue != null && showIds != null) {
      val tmdbId = showIds.tmdb
      val traktId = showIds.trakt
      if (tmdbId != null && traktId != null) {
        return BridgeListMember(
          item = FloppyListItemRef(FloppyWatchlistType.SHOWS, tmdbId),
          lookup = MemberLookup(traktId, show = showValue),
          changedAt = listed_at.toEpochMillis() ?: 0,
        )
      }
    }
    return null
  }

  private fun BridgeSyncState?.traktState() =
    BridgeObservedState(
      value = this?.traktValue,
      changedAt = this?.traktChangedAt ?: 0,
      observed = this?.traktObserved ?: false,
    )

  private fun BridgeSyncState?.floppyState() =
    BridgeObservedState(
      value = this?.floppyValue,
      changedAt = this?.floppyChangedAt ?: 0,
      observed = this?.floppyObserved ?: false,
    )

  private fun BridgeSyncState?.toState(
    domain: String,
    entityKey: String,
    trakt: BridgeObservedState,
    floppy: BridgeObservedState,
    resolvedValue: String?,
    resolvedAt: Long,
  ) = BridgeSyncState(
    domain = domain,
    entityKey = entityKey,
    traktValue = trakt.value,
    traktChangedAt = trakt.changedAt,
    traktObserved = trakt.observed,
    floppyValue = floppy.value,
    floppyChangedAt = floppy.changedAt,
    floppyObserved = floppy.observed,
    resolvedValue = resolvedValue,
    resolvedChangedAt = resolvedAt,
  )

  private fun TraktList.sharedMetadata() =
    SharedListMetadata(
      name = name.trim(),
      description = description.orEmpty(),
      isPublic = privacy == "public",
    )

  private fun FloppyBridgeList.sharedMetadata() =
    SharedListMetadata(
      name = name.trim(),
      description = description,
      isPublic = isPublic,
    )

  private fun SharedListMetadata.fingerprint(): String =
    sha256(
      listOf(name, description, isPublic.toString()).joinToString("\u0000"),
    )

  private fun SharedListMetadata.toTraktPrivacy() = if (isPublic) "public" else "private"

  private fun FloppyBridgeList.toTraktPrivacy() = if (isPublic) "public" else "private"

  private fun FloppyListItemRef.key() =
    when (type) {
      FloppyWatchlistType.MOVIES -> "m:$tmdbId"
      FloppyWatchlistType.SHOWS -> "s:$tmdbId"
    }

  private fun String.toListItemRef(): FloppyListItemRef? {
    val parts = split(':', limit = 2)
    if (parts.size != 2) return null
    val tmdbId = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
    val type = when (parts[0]) {
      "m" -> FloppyWatchlistType.MOVIES
      "s" -> FloppyWatchlistType.SHOWS
      else -> return null
    }
    return FloppyListItemRef(type, tmdbId)
  }

  private val BridgeListMember.key: String
    get() = item.key()

  private fun FloppyWatchlistType.localModeType() =
    when (this) {
      FloppyWatchlistType.MOVIES -> Mode.MOVIES.type
      FloppyWatchlistType.SHOWS -> Mode.SHOWS.type
    }

  private fun String?.toEpochMillis(): Long? = toUtcDateTime()?.toInstant()?.toEpochMilli()

  private fun sha256(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .joinToString("") { byte -> "%02x".format(byte) }

  private data class SharedListMetadata(
    val name: String,
    val description: String,
    val isPublic: Boolean,
  )

  private data class MemberLookup(
    val traktId: Long,
    val movie: Movie? = null,
    val show: Show? = null,
  )

  private data class BridgeListMember(
    val item: FloppyListItemRef,
    val lookup: MemberLookup,
    val changedAt: Long,
  )

  private data class MetadataResult(
    val local: CustomList,
    val trakt: TraktList,
    val floppy: FloppyBridgeList,
    val changes: Int,
  )
}
