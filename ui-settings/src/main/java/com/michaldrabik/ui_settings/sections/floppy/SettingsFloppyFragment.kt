package com.michaldrabik.ui_settings.sections.floppy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus.CONNECTED
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus.DISABLED
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus.INVALID_CONFIGURATION
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus.NOT_TESTED
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus.UNAUTHORIZED
import com.michaldrabik.data_remote.floppy.FloppyConnectionStatus.UNREACHABLE
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsFloppyBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFloppyFragment : BaseFragment<SettingsFloppyViewModel>(R.layout.fragment_settings_floppy) {

  override val viewModel by viewModels<SettingsFloppyViewModel>()
  private val binding by viewBinding(FragmentSettingsFloppyBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      doAfterLaunch = { viewModel.loadSettings() },
    )
  }

  private fun setupView() {
    with(binding) {
      settingsFloppyEnabled.onClick {
        viewModel.setEnabled(!settingsFloppyEnabledSwitch.isChecked)
      }
      settingsFloppyTest.onClick {
        viewModel.saveAndTest(
          baseUrl = settingsFloppyBaseUrlInput.text?.toString().orEmpty(),
          apiKey = settingsFloppyApiKeyInput.text?.toString().orEmpty(),
        )
      }
    }
  }

  private fun render(uiState: SettingsFloppyUiState) {
    with(binding) {
      val config = uiState.config
      settingsFloppyEnabledSwitch.isChecked = config.enabled
      settingsFloppyBaseUrlLayout.isEnabled = config.enabled
      settingsFloppyApiKeyLayout.isEnabled = config.enabled
      settingsFloppyTest.isEnabled = config.enabled && !uiState.isTesting
      settingsFloppyTest.alpha = if (config.enabled) 1F else 0.5F
      settingsFloppyTestProgress.visibleIf(uiState.isTesting)

      if (settingsFloppyBaseUrlInput.text?.toString() != config.baseUrl) {
        settingsFloppyBaseUrlInput.setText(config.baseUrl)
      }
      if (settingsFloppyApiKeyInput.text?.toString() != config.apiKey) {
        settingsFloppyApiKeyInput.setText(config.apiKey)
      }

      settingsFloppyStatus.text = getString(
        when (uiState.status) {
          DISABLED -> R.string.textSettingsFloppyStatusDisabled
          NOT_TESTED -> R.string.textSettingsFloppyStatusNotTested
          CONNECTED -> R.string.textSettingsFloppyStatusConnected
          UNAUTHORIZED -> R.string.textSettingsFloppyStatusUnauthorized
          UNREACHABLE -> R.string.textSettingsFloppyStatusUnreachable
          INVALID_CONFIGURATION -> R.string.textSettingsFloppyStatusInvalidConfiguration
        },
      )
    }
  }
}
