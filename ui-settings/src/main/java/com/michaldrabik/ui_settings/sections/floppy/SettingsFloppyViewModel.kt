package com.michaldrabik.ui_settings.sections.floppy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.data_remote.floppy.FloppyConfig
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.normalizeFloppyBaseUrl
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsFloppyViewModel @Inject constructor(
  private val floppyRemoteDataSource: FloppyRemoteDataSource,
) : ViewModel() {

  private val configState = MutableStateFlow(FloppyConfig())
  private val statusState = MutableStateFlow(FloppyConnectionStatus.DISABLED)
  private val testingState = MutableStateFlow(false)

  fun loadSettings() {
    val config = floppyRemoteDataSource.getConfig()
    configState.value = config
    if (config.enabled) {
      viewModelScope.launch { testConnection(config) }
    } else {
      statusState.value = FloppyConnectionStatus.DISABLED
    }
  }

  fun setEnabled(enabled: Boolean) {
    val config = configState.value.copy(enabled = enabled)
    configState.value = config
    floppyRemoteDataSource.saveConfig(config)
    statusState.value = if (enabled) FloppyConnectionStatus.NOT_TESTED else FloppyConnectionStatus.DISABLED
  }

  fun saveAndTest(
    baseUrl: String,
    apiKey: String,
  ) {
    viewModelScope.launch {
      val config = try {
        configState.value.copy(
          baseUrl = normalizeFloppyBaseUrl(baseUrl),
          apiKey = apiKey.trim(),
        )
      } catch (_: IllegalArgumentException) {
        statusState.value = FloppyConnectionStatus.INVALID_CONFIGURATION
        return@launch
      }

      configState.value = config
      floppyRemoteDataSource.saveConfig(config)
      testConnection(config)
    }
  }

  private suspend fun testConnection(config: FloppyConfig) {
    testingState.value = true
    try {
      statusState.value = floppyRemoteDataSource.validateConnection(config)
    } finally {
      testingState.value = false
    }
  }

  val uiState = combine(
    configState,
    statusState,
    testingState,
  ) { config, status, isTesting ->
    SettingsFloppyUiState(
      config = config,
      status = status,
      isTesting = isTesting,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = SettingsFloppyUiState(),
  )
}
