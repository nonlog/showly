package com.michaldrabik.repository.bridge

import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_local.sources.BridgeSyncStatesLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeSyncStateRepository @Inject constructor(
  private val localSource: BridgeSyncStatesLocalDataSource,
) {
  suspend fun get(
    domain: String,
    entityKey: String,
  ): BridgeSyncState? = localSource.get(domain, entityKey)

  suspend fun getAll(domain: String): List<BridgeSyncState> = localSource.getAll(domain)

  suspend fun save(state: BridgeSyncState) = localSource.upsert(state)

  suspend fun clearDomain(domain: String) = localSource.deleteDomain(domain)

  suspend fun clearAll() = localSource.deleteAll()
}
