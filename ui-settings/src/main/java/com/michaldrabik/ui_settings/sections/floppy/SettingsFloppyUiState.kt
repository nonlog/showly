package com.michaldrabik.ui_settings.sections.floppy

import com.michaldrabik.data_remote.floppy.FloppyConfig
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus

data class SettingsFloppyUiState(
  val config: FloppyConfig = FloppyConfig(),
  val status: FloppyConnectionStatus = FloppyConnectionStatus.DISABLED,
  val isTesting: Boolean = false,
  val isTraktAuthorized: Boolean = false,
  val bridge: FloppyBridgeRunUiState = FloppyBridgeRunUiState(),
)

data class FloppyBridgeRunUiState(
  val lastAttemptAt: Long = 0,
  val lastSuccessAt: Long = 0,
  val changes: Int = 0,
  val failedDomains: String = "",
  val pendingDomains: List<String> = emptyList(),
)
