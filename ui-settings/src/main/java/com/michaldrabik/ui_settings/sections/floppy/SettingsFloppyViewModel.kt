package com.michaldrabik.ui_settings.sections.floppy

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.michaldrabik.data_remote.floppy.FloppyConfig
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.normalizeFloppyBaseUrl
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.bridge.BridgeRetryRepository
import com.michaldrabik.ui_base.floppy.FloppyBridgeRetryWorker
import com.michaldrabik.ui_base.trakt.TraktSyncWorker
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SettingsFloppyViewModel @Inject constructor(
  private val floppyRemoteDataSource: FloppyRemoteDataSource,
  private val userTraktManager: UserTraktManager,
  private val bridgeRetryRepository: BridgeRetryRepository,
  private val workManager: WorkManager,
  @Named("miscPreferences") private val miscPreferences: SharedPreferences,
) : ViewModel() {

  private val configState = MutableStateFlow(FloppyConfig())
  private val statusState = MutableStateFlow(FloppyConnectionStatus.DISABLED)
  private val testingState = MutableStateFlow(false)
  private val traktAuthorizedState = MutableStateFlow(false)
  private val bridgeState = MutableStateFlow(FloppyBridgeRunUiState())

  fun loadSettings() {
    val config = floppyRemoteDataSource.getConfig()
    configState.value = config
    traktAuthorizedState.value = userTraktManager.isAuthorized()
    refreshBridgeStatus()
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
    if (!enabled) {
      FloppyBridgeRetryWorker.cancel(workManager)
    } else {
      viewModelScope.launch {
        if (bridgeRetryRepository.getAll().isNotEmpty()) {
          FloppyBridgeRetryWorker.schedule(workManager)
        }
      }
    }
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

      val identityChanged =
        configState.value.baseUrl != config.baseUrl || configState.value.apiKey != config.apiKey
      configState.value = config
      floppyRemoteDataSource.saveConfig(config)
      if (identityChanged) clearBridgeStatus()
      testConnection(config)
    }
  }

  fun syncNow() {
    if (!configState.value.enabled || statusState.value != FloppyConnectionStatus.CONNECTED) return
    if (!userTraktManager.isAuthorized()) return
    TraktSyncWorker.scheduleOneOff(
      workManager = workManager,
      isImport = true,
      isExport = true,
      isSilent = false,
    )
  }

  fun refreshBridgeStatus() {
    traktAuthorizedState.value = userTraktManager.isAuthorized()
    viewModelScope.launch {
      bridgeState.value = FloppyBridgeRunUiState(
        lastAttemptAt = miscPreferences.getLong(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_ATTEMPT, 0),
        lastSuccessAt = miscPreferences.getLong(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_SUCCESS, 0),
        changes = miscPreferences.getInt(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_CHANGES, 0),
        failedDomains = miscPreferences.getString(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_FAILURES, "").orEmpty(),
        pendingDomains = bridgeRetryRepository.getAll().map { it.domain },
      )
    }
  }

  private suspend fun clearBridgeStatus() {
    bridgeRetryRepository.clearAll()
    FloppyBridgeRetryWorker.cancel(workManager)
    miscPreferences
      .edit()
      .remove(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_ATTEMPT)
      .remove(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_SUCCESS)
      .remove(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_CHANGES)
      .remove(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_FAILURES)
      .apply()
    bridgeState.value = FloppyBridgeRunUiState()
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
    traktAuthorizedState,
    bridgeState,
  ) { config, status, isTesting, isTraktAuthorized, bridge ->
    SettingsFloppyUiState(
      config = config,
      status = status,
      isTesting = isTesting,
      isTraktAuthorized = isTraktAuthorized,
      bridge = bridge,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = SettingsFloppyUiState(),
  )
}
