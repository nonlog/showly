package com.michaldrabik.repository.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeConflictResolverTest {

  @Test
  fun `first absence is observation not deletion`() {
    val state = BridgeConflictResolver.observe(
      previous = BridgeObservedState(null, 0, false),
      currentValue = null,
      remoteChangedAt = null,
      observedAt = 1_000,
    )

    assertNull(state.value)
    assertEquals(0L, state.changedAt)
    assertEquals(true, state.observed)
  }

  @Test
  fun `disappearance becomes tombstone at observation time`() {
    val state = BridgeConflictResolver.observe(
      previous = BridgeObservedState("present", 500, true),
      currentValue = null,
      remoteChangedAt = null,
      observedAt = 1_000,
    )

    assertNull(state.value)
    assertEquals(1_000L, state.changedAt)
  }

  @Test
  fun `remote timestamp wins over observation time`() {
    val state = BridgeConflictResolver.observe(
      previous = BridgeObservedState(null, 0, true),
      currentValue = "present",
      remoteChangedAt = 750,
      observedAt = 1_000,
    )

    assertEquals("present", state.value)
    assertEquals(750L, state.changedAt)
  }

  @Test
  fun `newer floppy state wins`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState("present", 500, true),
      floppy = BridgeObservedState(null, 900, true),
    )

    assertNull(result.value)
    assertEquals(900L, result.changedAt)
    assertEquals(BridgeSide.FLOPPY, result.winner)
  }

  @Test
  fun `equal states converge without forcing a side`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState("8", 500, true),
      floppy = BridgeObservedState("8", 900, true),
      previousResolvedValue = "8",
      previousResolvedAt = 700,
    )

    assertEquals("8", result.value)
    assertEquals(900L, result.changedAt)
    assertNull(result.winner)
  }

  @Test
  fun `exact timestamp tie preserves previous resolution`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState("present", 900, true),
      floppy = BridgeObservedState(null, 900, true),
      previousResolvedValue = null,
      previousResolvedAt = 900,
    )

    assertNull(result.value)
    assertEquals(BridgeSide.FLOPPY, result.winner)
  }
}
