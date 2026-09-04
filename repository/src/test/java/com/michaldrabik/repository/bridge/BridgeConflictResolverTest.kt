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

  @Test
  fun `newer trakt value wins over older floppy value`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState("9", 1_200, true),
      floppy = BridgeObservedState("8", 1_100, true),
      previousResolvedValue = "8",
      previousResolvedAt = 1_100,
    )

    assertEquals("9", result.value)
    assertEquals(1_200L, result.changedAt)
    assertEquals(BridgeSide.TRAKT, result.winner)
  }

  @Test
  fun `newer trakt deletion wins over older floppy value`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState(null, 1_300, true),
      floppy = BridgeObservedState("present", 1_200, true),
      previousResolvedValue = "present",
      previousResolvedAt = 1_200,
    )

    assertNull(result.value)
    assertEquals(1_300L, result.changedAt)
    assertEquals(BridgeSide.TRAKT, result.winner)
  }

  @Test
  fun `newer trakt readd wins over older floppy tombstone`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState("present", 1_400, true),
      floppy = BridgeObservedState(null, 1_300, true),
      previousResolvedValue = null,
      previousResolvedAt = 1_300,
    )

    assertEquals("present", result.value)
    assertEquals(1_400L, result.changedAt)
    assertEquals(BridgeSide.TRAKT, result.winner)
  }

  @Test
  fun `newer floppy readd wins over older trakt tombstone`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState(null, 1_300, true),
      floppy = BridgeObservedState("present", 1_400, true),
      previousResolvedValue = null,
      previousResolvedAt = 1_300,
    )

    assertEquals("present", result.value)
    assertEquals(1_400L, result.changedAt)
    assertEquals(BridgeSide.FLOPPY, result.winner)
  }

  @Test
  fun `readd without provider timestamp uses observation time`() {
    val state = BridgeConflictResolver.observe(
      previous = BridgeObservedState(null, 1_000, true),
      currentValue = "present",
      remoteChangedAt = null,
      observedAt = 1_500,
    )

    assertEquals("present", state.value)
    assertEquals(1_500L, state.changedAt)
  }

  @Test
  fun `unchanged value without provider timestamp keeps mutation time`() {
    val state = BridgeConflictResolver.observe(
      previous = BridgeObservedState("present", 1_000, true),
      currentValue = "present",
      remoteChangedAt = null,
      observedAt = 2_000,
    )

    assertEquals("present", state.value)
    assertEquals(1_000L, state.changedAt)
  }

  @Test
  fun `provider timestamp advances unchanged state without using observation time`() {
    val state = BridgeConflictResolver.observe(
      previous = BridgeObservedState("present", 1_000, true),
      currentValue = "present",
      remoteChangedAt = 1_250,
      observedAt = 2_000,
    )

    assertEquals("present", state.value)
    assertEquals(1_250L, state.changedAt)
  }

  @Test
  fun `exact timestamp tie without prior resolution uses deterministic trakt winner`() {
    val result = BridgeConflictResolver.resolve(
      trakt = BridgeObservedState("present", 1_500, true),
      floppy = BridgeObservedState(null, 1_500, true),
    )

    assertEquals("present", result.value)
    assertEquals(1_500L, result.changedAt)
    assertEquals(BridgeSide.TRAKT, result.winner)
  }

  @Test
  fun `both first absences converge without inventing a deletion timestamp`() {
    val trakt = BridgeConflictResolver.observe(
      previous = BridgeObservedState(null, 0, false),
      currentValue = null,
      remoteChangedAt = null,
      observedAt = 1_000,
    )
    val floppy = BridgeConflictResolver.observe(
      previous = BridgeObservedState(null, 0, false),
      currentValue = null,
      remoteChangedAt = null,
      observedAt = 1_000,
    )
    val result = BridgeConflictResolver.resolve(trakt, floppy)

    assertNull(result.value)
    assertEquals(0L, result.changedAt)
    assertNull(result.winner)
  }
}
