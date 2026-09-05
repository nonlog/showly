package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.michaldrabik.data_local.database.model.BridgeRetryState
import com.michaldrabik.data_local.sources.BridgeRetryStatesLocalDataSource

@Dao
interface BridgeRetryStatesDao : BridgeRetryStatesLocalDataSource {

  @Query("SELECT * FROM bridge_retry_state WHERE domain = :domain LIMIT 1")
  override suspend fun get(domain: String): BridgeRetryState?

  @Query("SELECT * FROM bridge_retry_state ORDER BY queued_at ASC")
  override suspend fun getAll(): List<BridgeRetryState>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  override suspend fun upsert(state: BridgeRetryState)

  @Query("DELETE FROM bridge_retry_state WHERE domain = :domain")
  override suspend fun deleteDomain(domain: String)

  @Query("DELETE FROM bridge_retry_state")
  override suspend fun deleteAll()
}
