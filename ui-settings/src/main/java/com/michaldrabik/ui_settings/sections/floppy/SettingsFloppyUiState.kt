package com.michaldrabik.ui_settings.sections.floppy

import com.michaldrabik.data_remote.floppy.FloppyConfig
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus

data class SettingsFloppyUiState(
  val config: FloppyConfig = FloppyConfig(),
  val status: FloppyConnectionStatus = FloppyConnectionStatus.DISABLED,
  val isTesting: Boolean = false,
)
