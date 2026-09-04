package com.michaldrabik.data_remote.floppy

import android.content.SharedPreferences
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.time.OffsetDateTime
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

enum class FloppyHistoryType {
  MOVIES,
  EPISODES,
}

enum class FloppyWatchlistType {
  MOVIES,
  SHOWS,
}

data class FloppyConsumption(
  @Json(name = "consumption_id") val consumptionId: Long? = null,
  val status: Int? = null,
  @Json(name = "end_date") val endDate: String? = null,
)

data class FloppyMediaDetail(
  val consumptions: List<FloppyConsumption> = emptyList(),
)

internal data class FloppyMovieHistoryRequest(
  val source: String = "tmdb",
  @Json(name = "media_id") val mediaId: Long,
  val status: String = "Completed",
  @Json(name = "end_date") val endDate: String,
)

internal data class FloppyEpisodeHistoryRequest(
  @Json(name = "end_date") val endDate: String,
)

internal data class FloppyWatchlistRequest(
  val source: String = "tmdb",
  @Json(name = "media_id") val mediaId: Long,
  val status: Int = 0,
)

internal data class FloppyTrackedMediaResponse(
  @Json(name = "consumption_id") val consumptionId: Long? = null,
)

private data class FloppyHttpResponse(
  val code: Int,
  val body: String,
)

interface FloppyRemoteDataSource {
  fun getConfig(): FloppyConfig

  fun saveConfig(config: FloppyConfig)

  fun getHistoryCheckpoint(type: FloppyHistoryType): Long

  fun setHistoryCheckpoint(
    type: FloppyHistoryType,
    historyId: Long,
  )

  suspend fun validateConnection(config: FloppyConfig): FloppyConnectionStatus

  suspend fun ensureMovieHistory(
    tmdbId: Long,
    watchedAt: String,
  ): Boolean

  suspend fun ensureEpisodeHistory(
    showTmdbId: Long,
    season: Int,
    episode: Int,
    watchedAt: String,
  ): Boolean

  fun getOwnedWatchlistTmdbIds(type: FloppyWatchlistType): Set<Long>

  suspend fun ensureWatchlistPlanning(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): Boolean

  suspend fun removeOwnedWatchlistPlanning(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): Boolean
}

@Singleton
internal class DefaultFloppyRemoteDataSource @Inject constructor(
  @Named("networkPreferences") private val preferences: SharedPreferences,
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
  private val moshi: Moshi,
) : FloppyRemoteDataSource {

  companion object {
    private const val KEY_ENABLED = "FLOPPY_ENABLED"
    private const val KEY_BASE_URL = "FLOPPY_BASE_URL"
    private const val KEY_API_KEY = "FLOPPY_API_KEY"
    private const val KEY_HISTORY_MOVIES = "FLOPPY_HISTORY_MOVIES_CHECKPOINT"
    private const val KEY_HISTORY_EPISODES = "FLOPPY_HISTORY_EPISODES_CHECKPOINT"
    private const val KEY_WATCHLIST_MOVIES = "FLOPPY_WATCHLIST_MOVIES_OWNERSHIP"
    private const val KEY_WATCHLIST_SHOWS = "FLOPPY_WATCHLIST_SHOWS_OWNERSHIP"
    private const val STATUS_PLANNING = 0
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }

  private val mediaDetailAdapter = moshi.adapter(FloppyMediaDetail::class.java)
  private val movieHistoryAdapter = moshi.adapter(FloppyMovieHistoryRequest::class.java)
  private val episodeHistoryAdapter = moshi.adapter(FloppyEpisodeHistoryRequest::class.java)
  private val watchlistAdapter = moshi.adapter(FloppyWatchlistRequest::class.java)
  private val trackedMediaAdapter = moshi.adapter(FloppyTrackedMediaResponse::class.java)

  override fun getConfig() =
    FloppyConfig(
      enabled = preferences.getBoolean(KEY_ENABLED, false),
      baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
      apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
    )

  override fun saveConfig(config: FloppyConfig) {
    val previous = getConfig()
    val identityChanged = isFloppyIdentityChanged(previous, config)
    preferences
      .edit()
      .putBoolean(KEY_ENABLED, config.enabled)
      .putString(KEY_BASE_URL, config.baseUrl)
      .putString(KEY_API_KEY, config.apiKey)
      .apply {
        if (identityChanged) {
          remove(KEY_HISTORY_MOVIES)
          remove(KEY_HISTORY_EPISODES)
          remove(KEY_WATCHLIST_MOVIES)
          remove(KEY_WATCHLIST_SHOWS)
        }
      }.apply()
  }

  override fun getHistoryCheckpoint(type: FloppyHistoryType): Long = preferences.getLong(type.checkpointKey(), 0L)

  override fun setHistoryCheckpoint(
    type: FloppyHistoryType,
    historyId: Long,
  ) {
    preferences.edit().putLong(type.checkpointKey(), historyId).apply()
  }

  override suspend fun validateConnection(config: FloppyConfig): FloppyConnectionStatus {
    if (!config.enabled) return FloppyConnectionStatus.DISABLED
    val baseUrl = try {
      normalizeFloppyBaseUrl(config.baseUrl)
    } catch (_: IllegalArgumentException) {
      return FloppyConnectionStatus.INVALID_CONFIGURATION
    }
    val infoCode = try {
      executeRequest(
        Request
          .Builder()
          .url("$baseUrl/api/v1/info/")
          .get()
          .build(),
      ).code
    } catch (_: IOException) {
      return FloppyConnectionStatus.UNREACHABLE
    }
    if (infoCode !in 200..299) return FloppyConnectionStatus.UNREACHABLE
    if (config.apiKey.isBlank()) return FloppyConnectionStatus.UNAUTHORIZED
    val preferencesCode = try {
      executeRequest(
        Request
          .Builder()
          .url("$baseUrl/api/v1/user/preferences/")
          .header("X-API-Key", config.apiKey)
          .get()
          .build(),
      ).code
    } catch (_: IOException) {
      return FloppyConnectionStatus.UNREACHABLE
    }
    return when (preferencesCode) {
      in 200..299 -> FloppyConnectionStatus.CONNECTED
      401, 403 -> FloppyConnectionStatus.UNAUTHORIZED
      else -> FloppyConnectionStatus.UNREACHABLE
    }
  }

  override suspend fun ensureMovieHistory(
    tmdbId: Long,
    watchedAt: String,
  ): Boolean {
    val (config, baseUrl) = getSyncConfig()
    val detailResponse = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/media/movie/tmdb/$tmdbId/", config.apiKey).get().build(),
    )
    if (detailResponse.code in 200..299 && containsWatchedAt(detailResponse.body, watchedAt)) return false
    if (detailResponse.code != 404 && detailResponse.code !in 200..299) {
      throw IOException("Floppy movie detail request failed with HTTP " + detailResponse.code)
    }
    val body = movieHistoryAdapter
      .toJson(FloppyMovieHistoryRequest(mediaId = tmdbId, endDate = watchedAt))
      .toRequestBody(JSON_MEDIA_TYPE)
    val response = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/media/movie/", config.apiKey).post(body).build(),
    )
    if (response.code !in 200..299) {
      throw IOException("Floppy movie history write failed with HTTP " + response.code)
    }
    return true
  }

  override suspend fun ensureEpisodeHistory(
    showTmdbId: Long,
    season: Int,
    episode: Int,
    watchedAt: String,
  ): Boolean {
    val (config, baseUrl) = getSyncConfig()
    val detailResponse = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/tv/tmdb/$showTmdbId/$season/$episode/",
        config.apiKey,
      ).get().build(),
    )
    if (detailResponse.code in 200..299 && containsWatchedAt(detailResponse.body, watchedAt)) return false
    if (detailResponse.code != 404 && detailResponse.code !in 200..299) {
      throw IOException("Floppy episode detail request failed with HTTP " + detailResponse.code)
    }
    val body = episodeHistoryAdapter
      .toJson(FloppyEpisodeHistoryRequest(endDate = watchedAt))
      .toRequestBody(JSON_MEDIA_TYPE)
    val response = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/tv/tmdb/$showTmdbId/$season/episodes/$episode/watch/",
        config.apiKey,
      ).post(body).build(),
    )
    if (response.code !in 200..299) {
      throw IOException("Floppy episode history write failed with HTTP " + response.code)
    }
    return true
  }

  override fun getOwnedWatchlistTmdbIds(type: FloppyWatchlistType): Set<Long> = getWatchlistOwnership(type).keys

  override suspend fun ensureWatchlistPlanning(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): Boolean {
    require(tmdbId > 0) { "TMDB id must be positive" }
    val (config, baseUrl) = getSyncConfig()
    val mediaType = type.mediaTypePath()
    val detailResponse = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/", config.apiKey).get().build(),
    )
    val ownedConsumptionId = getWatchlistOwnership(type)[tmdbId]
    val detail = when {
      detailResponse.code in 200..299 -> parseMediaDetail(detailResponse.body)
      detailResponse.code == 404 -> null
      else -> throw IOException("Floppy watchlist detail request failed with HTTP " + detailResponse.code)
    }

    if (detail == null) {
      if (ownedConsumptionId != null) clearWatchlistOwnership(type, tmdbId)
    } else {
      val ownedPlanningStillExists = ownedConsumptionId != null &&
        detail.consumptions.any {
          it.consumptionId == ownedConsumptionId && it.status == STATUS_PLANNING
        }
      if (detail.consumptions.any { it.status == STATUS_PLANNING }) {
        if (ownedConsumptionId != null && !ownedPlanningStillExists) {
          clearWatchlistOwnership(type, tmdbId)
        }
        return false
      }
      if (ownedConsumptionId != null) clearWatchlistOwnership(type, tmdbId)
    }

    val body = watchlistAdapter
      .toJson(FloppyWatchlistRequest(mediaId = tmdbId, status = STATUS_PLANNING))
      .toRequestBody(JSON_MEDIA_TYPE)
    val response = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/media/$mediaType/", config.apiKey).post(body).build(),
    )
    if (response.code !in 200..299) {
      throw IOException("Floppy watchlist write failed with HTTP " + response.code)
    }
    val tracked = try {
      trackedMediaAdapter.fromJson(response.body)
    } catch (error: Exception) {
      throw IOException("Unable to parse Floppy watchlist write response", error)
    } ?: throw IOException("Floppy watchlist write response was empty")
    val consumptionId = tracked.consumptionId
      ?: throw IOException("Floppy watchlist write response did not include a consumption id")
    setWatchlistOwnership(type, tmdbId, consumptionId)
    return true
  }

  override suspend fun removeOwnedWatchlistPlanning(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): Boolean {
    val consumptionId = getWatchlistOwnership(type)[tmdbId] ?: return false
    val (config, baseUrl) = getSyncConfig()
    val mediaType = type.mediaTypePath()
    val detailResponse = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/", config.apiKey).get().build(),
    )
    if (detailResponse.code == 404) {
      clearWatchlistOwnership(type, tmdbId)
      return false
    }
    if (detailResponse.code !in 200..299) {
      throw IOException("Floppy watchlist detail request failed with HTTP " + detailResponse.code)
    }
    val detail = parseMediaDetail(detailResponse.body)
    val ownedConsumption = detail.consumptions.firstOrNull { it.consumptionId == consumptionId }
    if (ownedConsumption?.status != STATUS_PLANNING) {
      clearWatchlistOwnership(type, tmdbId)
      return false
    }

    val response = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/history/$consumptionId/",
        config.apiKey,
      ).delete().build(),
    )
    return when (response.code) {
      204 -> {
        clearWatchlistOwnership(type, tmdbId)
        true
      }
      404 -> {
        clearWatchlistOwnership(type, tmdbId)
        false
      }
      else -> throw IOException("Floppy watchlist delete failed with HTTP " + response.code)
    }
  }

  private fun containsWatchedAt(
    body: String,
    watchedAt: String,
  ): Boolean = parseMediaDetail(body).consumptions.any { sameFloppyInstant(it.endDate, watchedAt) }

  private fun parseMediaDetail(body: String): FloppyMediaDetail =
    try {
      mediaDetailAdapter.fromJson(body)
    } catch (error: Exception) {
      throw IOException("Unable to parse Floppy media detail response", error)
    } ?: throw IOException("Floppy media detail response was empty")

  private fun getWatchlistOwnership(type: FloppyWatchlistType): Map<Long, Long> =
    preferences
      .getStringSet(type.ownershipKey(), emptySet())
      .orEmpty()
      .mapNotNull(::decodeFloppyWatchlistOwnership)
      .toMap()

  private fun setWatchlistOwnership(
    type: FloppyWatchlistType,
    tmdbId: Long,
    consumptionId: Long,
  ) {
    val ownership = getWatchlistOwnership(type).toMutableMap()
    ownership[tmdbId] = consumptionId
    saveWatchlistOwnership(type, ownership)
  }

  private fun clearWatchlistOwnership(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ) {
    val ownership = getWatchlistOwnership(type).toMutableMap()
    if (ownership.remove(tmdbId) != null) saveWatchlistOwnership(type, ownership)
  }

  private fun saveWatchlistOwnership(
    type: FloppyWatchlistType,
    ownership: Map<Long, Long>,
  ) {
    val encoded = ownership
      .map { (tmdbId, consumptionId) ->
        encodeFloppyWatchlistOwnership(tmdbId, consumptionId)
      }.toSet()
    preferences.edit().putStringSet(type.ownershipKey(), encoded).apply()
  }

  private fun getSyncConfig(): Pair<FloppyConfig, String> {
    val config = getConfig()
    if (!config.enabled || config.apiKey.isBlank()) throw IOException("Floppy history sync is not configured")
    val baseUrl = try {
      normalizeFloppyBaseUrl(config.baseUrl)
    } catch (error: IllegalArgumentException) {
      throw IOException("Invalid Floppy base URL", error)
    }
    return config to baseUrl
  }

  private fun authenticatedRequestBuilder(
    url: String,
    apiKey: String,
  ) = Request.Builder().url(url).header("X-API-Key", apiKey)

  private suspend fun executeRequest(request: Request): FloppyHttpResponse =
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
              if (continuation.isActive) {
                continuation.resume(FloppyHttpResponse(it.code, it.body.string()))
              }
            }
          }
        },
      )
    }

  private fun FloppyHistoryType.checkpointKey() =
    when (this) {
      FloppyHistoryType.MOVIES -> KEY_HISTORY_MOVIES
      FloppyHistoryType.EPISODES -> KEY_HISTORY_EPISODES
    }

  private fun FloppyWatchlistType.ownershipKey() =
    when (this) {
      FloppyWatchlistType.MOVIES -> KEY_WATCHLIST_MOVIES
      FloppyWatchlistType.SHOWS -> KEY_WATCHLIST_SHOWS
    }

  private fun FloppyWatchlistType.mediaTypePath() =
    when (this) {
      FloppyWatchlistType.MOVIES -> "movie"
      FloppyWatchlistType.SHOWS -> "tv"
    }
}

internal fun isFloppyIdentityChanged(
  previous: FloppyConfig,
  current: FloppyConfig,
): Boolean = previous.baseUrl != current.baseUrl || previous.apiKey != current.apiKey

internal fun encodeFloppyWatchlistOwnership(
  tmdbId: Long,
  consumptionId: Long,
): String = "$tmdbId:$consumptionId"

internal fun decodeFloppyWatchlistOwnership(value: String): Pair<Long, Long>? {
  val parts = value.split(':', limit = 2)
  if (parts.size != 2) return null
  val tmdbId = parts[0].toLongOrNull() ?: return null
  val consumptionId = parts[1].toLongOrNull() ?: return null
  if (tmdbId <= 0 || consumptionId <= 0) return null
  return tmdbId to consumptionId
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

fun sameFloppyInstant(
  first: String?,
  second: String?,
): Boolean {
  if (first.isNullOrBlank() || second.isNullOrBlank()) return false
  return runCatching {
    OffsetDateTime.parse(first).toInstant() == OffsetDateTime.parse(second).toInstant()
  }.getOrElse { first == second }
}
