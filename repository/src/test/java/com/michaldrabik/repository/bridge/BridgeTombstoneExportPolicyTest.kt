package com.michaldrabik.repository.bridge

import com.michaldrabik.data_local.database.model.BridgeSyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTombstoneExportPolicyTest {

  @Test
  fun `newer tombstone suppresses stale local export`() {
    assertTrue(
      BridgeTombstoneExportPolicy.shouldSuppress(
        state = state(resolvedValue = null, resolvedChangedAt = 2_000),
        localChangedAt = 1_000,
      ),
    )
  }

  @Test
  fun `equal-time tombstone suppresses duplicate resurrection`() {
    assertTrue(
      BridgeTombstoneExportPolicy.shouldSuppress(
        state = state(resolvedValue = null, resolvedChangedAt = 1_000),
        localChangedAt = 1_000,
      ),
    )
  }

  @Test
  fun `older tombstone does not suppress newer local mutation`() {
    assertFalse(
      BridgeTombstoneExportPolicy.shouldSuppress(
        state = state(resolvedValue = null, resolvedChangedAt = 900),
        localChangedAt = 1_000,
      ),
    )
  }

  @Test
  fun `resolved presence never suppresses export`() {
    assertFalse(
      BridgeTombstoneExportPolicy.shouldSuppress(
        state = state(resolvedValue = "present", resolvedChangedAt = 2_000),
        localChangedAt = 1_000,
      ),
    )
  }

  @Test
  fun `missing bridge state never suppresses export`() {
    assertFalse(BridgeTombstoneExportPolicy.shouldSuppress(null, 1_000))
  }

  private fun state(
    resolvedValue: String?,
    resolvedChangedAt: Long,
  ) = BridgeSyncState(
    domain = "history",
    entityKey = "m:1:1000",
    traktValue = resolvedValue,
    traktChangedAt = resolvedChangedAt,
    traktObserved = true,
    floppyValue = resolvedValue,
    floppyChangedAt = resolvedChangedAt,
    floppyObserved = true,
    resolvedValue = resolvedValue,
    resolvedChangedAt = resolvedChangedAt,
  )
}
