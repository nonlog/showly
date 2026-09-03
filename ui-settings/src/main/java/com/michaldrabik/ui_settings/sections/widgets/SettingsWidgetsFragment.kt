package com.michaldrabik.ui_settings.sections.widgets

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsWidgetsBinding
import com.michaldrabik.ui_settings.helpers.AppTheme
import com.michaldrabik.ui_settings.helpers.WidgetTransparency
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsWidgetsFragment : BaseFragment<SettingsWidgetsViewModel>(R.layout.fragment_settings_widgets) {

  override val viewModel by viewModels<SettingsWidgetsViewModel>()
  private val binding by viewBinding(FragmentSettingsWidgetsBinding::bind)

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
    binding.settingsWidgetsLabels.onClick {
      viewModel.enableWidgetsTitles(!binding.settingsWidgetsLabelsSwitch.isChecked, requireAppContext())
    }
  }

  private fun render(uiState: SettingsWidgetsUiState) {
    with(binding) {
      uiState.settings?.let { settingsWidgetsLabelsSwitch.isChecked = it.widgetsShowLabel }

      settingsWidgetsThemeValue.setText(uiState.themeWidgets.displayName)
      settingsWidgetsTheme.onClick { showThemeDialog(uiState.themeWidgets) }

      settingsWidgetsTransparencyValue.setText(uiState.widgetsTransparency.displayName)
      settingsWidgetsTransparency.onClick { showTransparencyDialog(uiState.widgetsTransparency) }
    }
  }

  private fun showThemeDialog(current: AppTheme) {
    val options = listOf(AppTheme.DARK, AppTheme.LIGHT)
    val selected = options.indexOf(current)
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setSingleChoiceItems(options.map { getString(it.displayName) }.toTypedArray(), selected) { dialog, index ->
        if (index != selected) viewModel.setWidgetsTheme(options[index], requireAppContext())
        dialog.dismiss()
      }.show()
  }

  private fun showTransparencyDialog(current: WidgetTransparency) {
    val options = WidgetTransparency.entries
    val selected = options.indexOf(current)
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setSingleChoiceItems(options.map { getString(it.displayName) }.toTypedArray(), selected) { dialog, index ->
        if (index != selected) viewModel.setWidgetsTransparency(options[index], requireAppContext())
        dialog.dismiss()
      }.show()
  }
}
