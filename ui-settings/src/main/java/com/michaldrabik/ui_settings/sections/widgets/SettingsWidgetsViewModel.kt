package com.michaldrabik.ui_settings.sections.widgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_model.Settings
import com.michaldrabik.ui_settings.helpers.AppTheme
import com.michaldrabik.ui_settings.helpers.WidgetTransparency
import com.michaldrabik.ui_settings.sections.widgets.cases.SettingsWidgetsMainCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsWidgetsViewModel @Inject constructor(
  private val mainCase: SettingsWidgetsMainCase,
) : ViewModel() {

  private val settingsState = MutableStateFlow<Settings?>(null)
  private val widgetThemeState = MutableStateFlow(AppTheme.DARK)
  private val widgetTransparencyState = MutableStateFlow(WidgetTransparency.SOLID)

  fun loadSettings() {
    viewModelScope.launch { refreshSettings() }
  }

  fun enableWidgetsTitles(
    enable: Boolean,
    context: Context,
  ) {
    viewModelScope.launch {
      mainCase.enableWidgetsTitles(enable, context)
      refreshSettings()
    }
  }

  fun setWidgetsTheme(
    theme: AppTheme,
    context: Context,
  ) {
    viewModelScope.launch {
      mainCase.setWidgetsTheme(theme.code, context)
      refreshSettings()
    }
  }

  fun setWidgetsTransparency(
    transparency: WidgetTransparency,
    context: Context,
  ) {
    viewModelScope.launch {
      mainCase.setWidgetsTransparency(transparency.value, context)
      refreshSettings()
    }
  }

  private suspend fun refreshSettings() {
    settingsState.value = mainCase.getSettings()
    widgetThemeState.value = AppTheme.fromCode(mainCase.getWidgetsTheme())
    widgetTransparencyState.value = WidgetTransparency.fromValue(mainCase.getWidgetsTransparency())
  }

  val uiState = combine(
    settingsState,
    widgetThemeState,
    widgetTransparencyState,
  ) { settings, theme, transparency ->
    SettingsWidgetsUiState(
      settings = settings,
      themeWidgets = theme,
      widgetsTransparency = transparency,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = SettingsWidgetsUiState(),
  )
}
