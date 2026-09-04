package com.michaldrabik.data_local.sources

import com.michaldrabik.data_local.database.model.BridgeSyncState

interface BridgeSyncStatesLocalDataSource {
  suspend fun get(
    domain: String,
    entityKey: String,
  ): BridgeSyncState?

  suspend fun getAll(domain: String): List<BridgeSyncState>

  suspend fun upsert(state: BridgeSyncState)

  suspend fun deleteDomain(domain: String)

  suspend fun deleteAll()
}
