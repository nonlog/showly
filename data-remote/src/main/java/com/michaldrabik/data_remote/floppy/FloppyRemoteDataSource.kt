package com.michaldrabik.data_remote.floppy

import android.content.SharedPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FloppyConfig(
  val enabled: Boolean = false,
  val baseUrl: String = "",
  val apiKey: String = "",
)

enum class FloppyConnectionStatus {
  DISABLED,
  NOT_TESTED,
  CONNECTED,
  UNAUTHORIZED,
  UNREACHABLE,
  INVALID_CONFIGURATION,
}

interface FloppyRemoteDataSource {
  fun getConfig(): FloppyConfig

  fun saveConfig(config: FloppyConfig)

  suspend fun validateConnection(config: FloppyConfig): FloppyConnectionStatus
}

@Singleton
internal class DefaultFloppyRemoteDataSource @Inject constructor(
  @Named("networkPreferences") private val preferences: SharedPreferences,
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
) : FloppyRemoteDataSource {

  companion object {
    private const val KEY_ENABLED = "FLOPPY_ENABLED"
    private const val KEY_BASE_URL = "FLOPPY_BASE_URL"
    private const val KEY_API_KEY = "FLOPPY_API_KEY"
  }

  override fun getConfig() = FloppyConfig(
    enabled = preferences.getBoolean(KEY_ENABLED, false),
    baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
    apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
  )

  override fun saveConfig(config: FloppyConfig) {
    preferences
      .edit()
      .putBoolean(KEY_ENABLED, config.enabled)
      .putString(KEY_BASE_URL, config.baseUrl)
      .putString(KEY_API_KEY, config.apiKey)
      .apply()
  }

  override suspend fun validateConnection(config: FloppyConfig): FloppyConnectionStatus {
    if (!config.enabled) return FloppyConnectionStatus.DISABLED

    val baseUrl = try {
      normalizeFloppyBaseUrl(config.baseUrl)
    } catch (_: IllegalArgumentException) {
      return FloppyConnectionStatus.INVALID_CONFIGURATION
    }

    val infoCode = try {
      requestCode(
        Request
          .Builder()
          .url("$baseUrl/api/v1/info/")
          .get()
          .build(),
      )
    } catch (_: IOException) {
      return FloppyConnectionStatus.UNREACHABLE
    }

    if (infoCode !in 200..299) return FloppyConnectionStatus.UNREACHABLE
    if (config.apiKey.isBlank()) return FloppyConnectionStatus.UNAUTHORIZED

    val preferencesCode = try {
      requestCode(
        Request
          .Builder()
          .url("$baseUrl/api/v1/user/preferences/")
          .header("X-API-Key", config.apiKey)
          .get()
          .build(),
      )
    } catch (_: IOException) {
      return FloppyConnectionStatus.UNREACHABLE
    }

    return when (preferencesCode) {
      in 200..299 -> FloppyConnectionStatus.CONNECTED
      401, 403 -> FloppyConnectionStatus.UNAUTHORIZED
      else -> FloppyConnectionStatus.UNREACHABLE
    }
  }

  private suspend fun requestCode(request: Request): Int =
    suspendCancellableCoroutine { continuation ->
      val call = okHttpClient.newCall(request)
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(
        object : Callback {
          override fun onFailure(
            call: Call,
            e: IOException,
          ) {
            if (continuation.isActive) continuation.resumeWithException(e)
          }

          override fun onResponse(
            call: Call,
            response: Response,
          ) {
            response.use {
              if (continuation.isActive) continuation.resume(it.code)
            }
          }
        },
      )
    }
}

fun normalizeFloppyBaseUrl(value: String): String {
  val normalized = value.trim().trimEnd('/')
  require(normalized.isNotEmpty()) { "Floppy base URL is empty" }

  val uri = try {
    URI(normalized)
  } catch (error: Exception) {
    throw IllegalArgumentException("Invalid Floppy base URL", error)
  }

  val scheme = uri.scheme?.lowercase()
  require(scheme == "http" || scheme == "https") { "Floppy base URL must use HTTP or HTTPS" }
  require(!uri.host.isNullOrBlank()) { "Floppy base URL must contain a host" }
  require(uri.userInfo == null) { "Credentials must not be embedded in the Floppy base URL" }
  require(uri.query == null) { "Floppy base URL must not contain a query" }
  require(uri.fragment == null) { "Floppy base URL must not contain a fragment" }

  return normalized
}
