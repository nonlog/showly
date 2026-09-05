package com.michaldrabik.repository.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePrepassPolicyTest {

  @Test
  fun `legacy export is always allowed when bridge is disabled`() {
    assertTrue(BridgePrepassPolicy.canRunLegacyExport(bridgeEnabled = false, prepassResult = null))
  }

  @Test
  fun `successful bridge prepass allows legacy export`() {
    assertTrue(BridgePrepassPolicy.canRunLegacyExport(bridgeEnabled = true, prepassResult = 0))
    assertTrue(BridgePrepassPolicy.canRunLegacyExport(bridgeEnabled = true, prepassResult = 3))
  }

  @Test
  fun `failed bridge prepass blocks legacy export`() {
    assertFalse(BridgePrepassPolicy.canRunLegacyExport(bridgeEnabled = true, prepassResult = null))
  }
}
