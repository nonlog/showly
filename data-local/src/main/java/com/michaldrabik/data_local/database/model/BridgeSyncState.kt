package com.michaldrabik.data_local.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
  tableName = "bridge_sync_state",
  primaryKeys = ["domain", "entity_key"],
)
data class BridgeSyncState(
  @ColumnInfo(name = "domain") val domain: String,
  @ColumnInfo(name = "entity_key") val entityKey: String,
  @ColumnInfo(name = "trakt_value") val traktValue: String? = null,
  @ColumnInfo(name = "trakt_changed_at") val traktChangedAt: Long = 0,
  @ColumnInfo(name = "trakt_observed") val traktObserved: Boolean = false,
  @ColumnInfo(name = "floppy_value") val floppyValue: String? = null,
  @ColumnInfo(name = "floppy_changed_at") val floppyChangedAt: Long = 0,
  @ColumnInfo(name = "floppy_observed") val floppyObserved: Boolean = false,
  @ColumnInfo(name = "resolved_value") val resolvedValue: String? = null,
  @ColumnInfo(name = "resolved_changed_at") val resolvedChangedAt: Long = 0,
)
