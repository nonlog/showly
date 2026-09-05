package com.michaldrabik.repository.bridge

object BridgePrepassPolicy {
  fun canRunLegacyExport(
    bridgeEnabled: Boolean,
    prepassResult: Int?,
  ): Boolean = !bridgeEnabled || prepassResult != null
}
