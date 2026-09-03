package com.michaldrabik.ui_settings.sections.misc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.michaldrabik.data_remote.credentials.RuntimeCredentialOverrides
import com.michaldrabik.data_remote.credentials.RuntimeCredentialsStore
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.sections.misc.cases.SettingsMiscCacheCase
import com.michaldrabik.ui_settings.sections.misc.cases.SettingsMiscUserCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsMiscViewModel @Inject constructor(
  private val userCase: SettingsMiscUserCase,
  private val cacheCase: SettingsMiscCacheCase,
  private val runtimeCredentials: RuntimeCredentialsStore,
  private val userTraktManager: UserTraktManager,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private val userIdState = MutableStateFlow("")
  private val loadingState = MutableStateFlow(false)
  private val runtimeCredentialsState = MutableStateFlow(runtimeCredentials.overrides().hasAnyOverride)

  fun loadSettings() {
    viewModelScope.launch {
      userIdState.value = userCase.getUserId()
      runtimeCredentialsState.value = runtimeCredentials.overrides().hasAnyOverride
    }
  }

  fun getRuntimeCredentialOverrides(): RuntimeCredentialOverrides = runtimeCredentials.overrides()

  fun saveRuntimeCredentials(
    traktClientId: String,
    traktClientSecret: String,
    tmdbReadAccessToken: String,
  ) {
    viewModelScope.launch {
      val clientId = traktClientId.trim()
      val clientSecret = traktClientSecret.trim()
      val tmdbToken = tmdbReadAccessToken.trim()
      if (clientId.isBlank() != clientSecret.isBlank()) {
        messageChannel.send(MessageEvent.Error(R.string.textRuntimeCredentialsTraktPairRequired))
        return@launch
      }

      val current = runtimeCredentials.overrides()
      val nextId = clientId.takeIf { it.isNotBlank() }
      val nextSecret = clientSecret.takeIf { it.isNotBlank() }
      val traktChanged = current.traktClientId != nextId || current.traktClientSecret != nextSecret

      if (traktChanged && userTraktManager.isAuthorized()) {
        userTraktManager.revokeToken()
      }

      runtimeCredentials.saveOverrides(clientId, clientSecret, tmdbToken)
      runtimeCredentialsState.value = runtimeCredentials.overrides().hasAnyOverride
      messageChannel.send(
        MessageEvent.Info(
          if (traktChanged) R.string.textRuntimeCredentialsSavedReauth else R.string.textRuntimeCredentialsSaved,
        ),
      )
    }
  }

  fun restoreRuntimeCredentials() {
    viewModelScope.launch {
      val traktChanged = runtimeCredentials.overrides().hasTraktOverride
      if (traktChanged && userTraktManager.isAuthorized()) {
        userTraktManager.revokeToken()
      }
      runtimeCredentials.restoreRepositoryDefaults()
      runtimeCredentialsState.value = false
      messageChannel.send(
        MessageEvent.Info(
          if (traktChanged) R.string.textRuntimeCredentialsRestoredReauth else R.string.textRuntimeCredentialsRestored,
        ),
      )
    }
  }

  fun deleteImagesCache(context: Context) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { Glide.get(context).clearDiskCache() }
      Glide.get(context).clearMemory()
      cacheCase.deleteImagesCache()
      messageChannel.send(MessageEvent.Info(R.string.textImagesCacheCleared))
    }
  }

  val uiState = combine(
    userIdState,
    loadingState,
    runtimeCredentialsState,
  ) { s1, _, s3 ->
    SettingsMiscUiState(
      userId = s1,
      hasRuntimeCredentialOverrides = s3,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = SettingsMiscUiState(),
  )
}
