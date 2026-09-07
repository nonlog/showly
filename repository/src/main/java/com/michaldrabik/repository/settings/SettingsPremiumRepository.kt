package com.michaldrabik.repository.settings

import android.content.SharedPreferences
import com.michaldrabik.repository.utilities.BooleanPreference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SettingsPremiumRepository @Inject constructor(
  @Named("miscPreferences") preferences: SharedPreferences,
) {
  companion object {
    private const val KEY_PREMIUM = "KEY_PREMIUM"
    private const val KEY_SHOW_PREMIUM_EXPIRED = "KEY_SHOW_PREMIUM_EXPIRED"
    private const val KEY_SHOW_PAYWALL = "KEY_SHOW_PAYWALL"
  }

  var isPremium by BooleanPreference(preferences, KEY_PREMIUM, false)
  var showPremiumExpired by BooleanPreference(preferences, KEY_SHOW_PREMIUM_EXPIRED, false)
  var showPaywall by BooleanPreference(preferences, KEY_SHOW_PAYWALL, false)
}
