package com.michaldrabik.data_local.sources

import com.michaldrabik.data_local.database.model.BridgeRetryState

interface BridgeRetryStatesLocalDataSource {
  suspend fun get(domain: String): BridgeRetryState?

  suspend fun getAll(): List<BridgeRetryState>

  suspend fun upsert(state: BridgeRetryState)

  suspend fun deleteDomain(domain: String)

  suspend fun deleteAll()
}
