package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_local.sources.BridgeSyncStatesLocalDataSource

@Dao
interface BridgeSyncStatesDao : BridgeSyncStatesLocalDataSource {

  @Query("SELECT * FROM bridge_sync_state WHERE domain = :domain AND entity_key = :entityKey LIMIT 1")
  override suspend fun get(
    domain: String,
    entityKey: String,
  ): BridgeSyncState?

  @Query("SELECT * FROM bridge_sync_state WHERE domain = :domain")
  override suspend fun getAll(domain: String): List<BridgeSyncState>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  override suspend fun upsert(state: BridgeSyncState)

  @Query("DELETE FROM bridge_sync_state WHERE domain = :domain")
  override suspend fun deleteDomain(domain: String)

  @Query("DELETE FROM bridge_sync_state")
  override suspend fun deleteAll()
}
