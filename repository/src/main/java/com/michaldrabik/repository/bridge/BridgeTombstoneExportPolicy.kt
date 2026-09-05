package com.michaldrabik.repository.bridge

import com.michaldrabik.data_local.database.model.BridgeSyncState

object BridgeTombstoneExportPolicy {
  fun shouldSuppress(
    state: BridgeSyncState?,
    localChangedAt: Long,
  ): Boolean {
    if (state == null) return false
    return state.resolvedValue == null && state.resolvedChangedAt >= localChangedAt
  }
}
