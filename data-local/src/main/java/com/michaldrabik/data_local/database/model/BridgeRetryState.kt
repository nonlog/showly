package com.michaldrabik.data_local.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bridge_retry_state")
data class BridgeRetryState(
  @PrimaryKey @ColumnInfo(name = "domain") val domain: String,
  @ColumnInfo(name = "queued_at") val queuedAt: Long,
  @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
  @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long = 0,
  @ColumnInfo(name = "last_error") val lastError: String? = null,
)
