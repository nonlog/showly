package com.michaldrabik.data_remote.floppy

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

internal const val FLOPPY_KEY_ENABLED = "FLOPPY_ENABLED"
internal const val FLOPPY_KEY_BASE_URL = "FLOPPY_BASE_URL"
internal const val FLOPPY_KEY_API_KEY = "FLOPPY_API_KEY"

/**
 * Lightweight Floppy configuration access for startup-sensitive code.
 *
 * Keep this separate from [FloppyRemoteDataSource]: reading whether the bridge is enabled
 * must not construct OkHttp, Moshi, or the Floppy network adapters during app startup.
 */
@Singleton
class FloppyConfigStore @Inject constructor(
  @Named("networkPreferences") private val preferences: SharedPreferences,
) {
  fun getConfig() =
    FloppyConfig(
      enabled = preferences.getBoolean(FLOPPY_KEY_ENABLED, false),
      baseUrl = preferences.getString(FLOPPY_KEY_BASE_URL, "").orEmpty(),
      apiKey = preferences.getString(FLOPPY_KEY_API_KEY, "").orEmpty(),
    )

  fun isEnabled(): Boolean = preferences.getBoolean(FLOPPY_KEY_ENABLED, false)
}
