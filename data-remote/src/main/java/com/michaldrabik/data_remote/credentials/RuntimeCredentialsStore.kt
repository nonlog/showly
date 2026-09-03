package com.michaldrabik.data_remote.credentials

import android.content.SharedPreferences
import android.net.Uri
import com.michaldrabik.data_remote.BuildConfig
import com.michaldrabik.data_remote.Config
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class RuntimeCredentialOverrides(
  val traktClientId: String? = null,
  val traktClientSecret: String? = null,
  val tmdbReadAccessToken: String? = null,
) {
  val hasTraktOverride: Boolean
    get() = !traktClientId.isNullOrBlank() && !traktClientSecret.isNullOrBlank()

  val hasTmdbOverride: Boolean
    get() = !tmdbReadAccessToken.isNullOrBlank()

  val hasAnyOverride: Boolean
    get() = hasTraktOverride || hasTmdbOverride
}

@Singleton
class RuntimeCredentialsStore @Inject constructor(
  @Named("runtimeCredentialsPreferences") private val preferences: SharedPreferences,
) {

  companion object {
    private const val KEY_TRAKT_CLIENT_ID = "RUNTIME_TRAKT_CLIENT_ID"
    private const val KEY_TRAKT_CLIENT_SECRET = "RUNTIME_TRAKT_CLIENT_SECRET"
    private const val KEY_TMDB_READ_ACCESS_TOKEN = "RUNTIME_TMDB_READ_ACCESS_TOKEN"
  }

  val traktClientId: String
    get() = overrides().traktClientId ?: BuildConfig.TRAKT_CLIENT_ID

  val traktClientSecret: String
    get() = overrides().traktClientSecret ?: BuildConfig.TRAKT_CLIENT_SECRET

  val tmdbReadAccessToken: String
    get() = overrides().tmdbReadAccessToken ?: BuildConfig.TMDB_API_KEY

  fun overrides() = RuntimeCredentialOverrides(
    traktClientId = preferences.getString(KEY_TRAKT_CLIENT_ID, null)?.takeIf { it.isNotBlank() },
    traktClientSecret = preferences.getString(KEY_TRAKT_CLIENT_SECRET, null)?.takeIf { it.isNotBlank() },
    tmdbReadAccessToken = preferences.getString(KEY_TMDB_READ_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() },
  )

  fun saveOverrides(
    traktClientId: String?,
    traktClientSecret: String?,
    tmdbReadAccessToken: String?,
  ) {
    val clientId = traktClientId?.trim().orEmpty()
    val clientSecret = traktClientSecret?.trim().orEmpty()
    val tmdbToken = tmdbReadAccessToken?.trim().orEmpty()

    require(clientId.isBlank() == clientSecret.isBlank()) {
      "Trakt client id and secret must both be set or both be empty."
    }

    preferences.edit().apply {
      if (clientId.isBlank()) {
        remove(KEY_TRAKT_CLIENT_ID)
        remove(KEY_TRAKT_CLIENT_SECRET)
      } else {
        putString(KEY_TRAKT_CLIENT_ID, clientId)
        putString(KEY_TRAKT_CLIENT_SECRET, clientSecret)
      }
      if (tmdbToken.isBlank()) {
        remove(KEY_TMDB_READ_ACCESS_TOKEN)
      } else {
        putString(KEY_TMDB_READ_ACCESS_TOKEN, tmdbToken)
      }
    }.apply()
  }

  fun restoreRepositoryDefaults() {
    preferences.edit().clear().apply()
  }

  fun traktAuthorizeUrl(): String =
    Uri.Builder()
      .scheme("https")
      .authority("trakt.tv")
      .appendPath("oauth")
      .appendPath("authorize")
      .appendQueryParameter("response_type", "code")
      .appendQueryParameter("client_id", traktClientId)
      .appendQueryParameter("redirect_uri", Config.TRAKT_REDIRECT_URL)
      .build()
      .toString()
}
