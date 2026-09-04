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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val KEY_FLOPPY_LISTS_OWNERSHIP = "FLOPPY_LISTS_OWNERSHIP"

internal data class FloppyListRequest(
  val name: String,
  val description: String,
  @Json(name = "is_public") val isPublic: Boolean,
)

internal data class FloppyListResponse(
  val id: Long,
)

data class FloppyListItemRef(
  val type: FloppyWatchlistType,
  val tmdbId: Long,
)

private data class FloppyListHttpResponse(
  val code: Int,
  val body: String,
)

interface FloppyListsRemoteDataSource {
  fun getOwnedLocalListIds(): Set<Long>

  suspend fun ensureOwnedList(
    localListId: Long,
    name: String,
    description: String?,
    isPublic: Boolean,
  ): Boolean

  fun releaseOwnedList(localListId: Long): Boolean

  suspend fun ensureListItem(
    localListId: Long,
    item: FloppyListItemRef,
  ): Boolean
}

@Singleton
internal class DefaultFloppyListsRemoteDataSource @Inject constructor(
  private val floppySource: FloppyRemoteDataSource,
  @Named("networkPreferences") private val preferences: SharedPreferences,
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
  private val moshi: Moshi,
) : FloppyListsRemoteDataSource {

  companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }

  private val listRequestAdapter = moshi.adapter(FloppyListRequest::class.java)
  private val listResponseAdapter = moshi.adapter(FloppyListResponse::class.java)

  override fun getOwnedLocalListIds(): Set<Long> = getListOwnership().keys

  override suspend fun ensureOwnedList(
    localListId: Long,
    name: String,
    description: String?,
    isPublic: Boolean,
  ): Boolean {
    require(localListId > 0) { "Local list id must be positive" }
    require(name.isNotBlank()) { "List name must not be blank" }
    val (config, baseUrl) = getSyncConfig()
    val requestBody = listRequestAdapter
      .toJson(
        FloppyListRequest(
          name = name.trim(),
          description = description.orEmpty(),
          isPublic = isPublic,
        ),
      ).toRequestBody(JSON_MEDIA_TYPE)
    val ownedListId = getListOwnership()[localListId]
    if (ownedListId != null) {
      val response = executeRequest(
        authenticatedRequestBuilder("$baseUrl/api/v1/lists/$ownedListId/", config.apiKey)
          .patch(requestBody)
          .build(),
      )
      when {
        response.code in 200..299 -> return false
        response.code == 404 -> clearListOwnership(localListId)
        else -> throw IOException("Floppy list update failed with HTTP " + response.code)
      }
    }

    val response = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/lists/", config.apiKey)
        .post(requestBody)
        .build(),
    )
    if (response.code !in 200..299) {
      throw IOException("Floppy list creation failed with HTTP " + response.code)
    }
    val remoteListId = parseListId(response.body)
    setListOwnership(localListId, remoteListId)
    return true
  }

  override fun releaseOwnedList(localListId: Long): Boolean {
    val ownership = getListOwnership().toMutableMap()
    val removed = ownership.remove(localListId) != null
    if (removed) saveListOwnership(ownership)
    return removed
  }

  override suspend fun ensureListItem(
    localListId: Long,
    item: FloppyListItemRef,
  ): Boolean {
    require(item.tmdbId > 0) { "TMDB id must be positive" }
    val remoteListId = getListOwnership()[localListId]
      ?: throw IOException("Floppy list ownership is missing for local list $localListId")
    val (config, baseUrl) = getSyncConfig()

    var response = putListItem(baseUrl, config.apiKey, remoteListId, item)
    if (response.code == 404) {
      val syncResponse = executeRequest(
        authenticatedRequestBuilder(
          "$baseUrl/api/v1/media/${item.type.mediaTypePath()}/tmdb/${item.tmdbId}/sync/",
          config.apiKey,
        ).post("{}".toRequestBody(JSON_MEDIA_TYPE)).build(),
      )
      if (syncResponse.code !in 200..299 && syncResponse.code != 429) {
        throw IOException("Floppy list item metadata sync failed with HTTP " + syncResponse.code)
      }
      response = putListItem(baseUrl, config.apiKey, remoteListId, item)
    }

    return when (response.code) {
      in 200..299 -> true
      409 -> false
      else -> throw IOException("Floppy list item addition failed with HTTP " + response.code)
    }
  }

  private suspend fun putListItem(
    baseUrl: String,
    apiKey: String,
    remoteListId: Long,
    item: FloppyListItemRef,
  ) = executeRequest(
    authenticatedRequestBuilder(
      "$baseUrl/api/v1/media/${item.type.mediaTypePath()}/tmdb/${item.tmdbId}/lists/$remoteListId/",
      apiKey,
    ).put("{}".toRequestBody(JSON_MEDIA_TYPE)).build(),
  )

  private fun parseListId(body: String): Long {
    val response = try {
      listResponseAdapter.fromJson(body)
    } catch (error: Exception) {
      throw IOException("Unable to parse Floppy list response", error)
    } ?: throw IOException("Floppy list response was empty")
    if (response.id <= 0) throw IOException("Floppy list response contained an invalid id")
    return response.id
  }

  private fun getListOwnership(): Map<Long, Long> =
    preferences
      .getStringSet(KEY_FLOPPY_LISTS_OWNERSHIP, emptySet())
      .orEmpty()
      .mapNotNull(::decodeFloppyListOwnership)
      .toMap()

  private fun setListOwnership(
    localListId: Long,
    remoteListId: Long,
  ) {
    val ownership = getListOwnership().toMutableMap()
    ownership[localListId] = remoteListId
    saveListOwnership(ownership)
  }

  private fun clearListOwnership(localListId: Long) {
    val ownership = getListOwnership().toMutableMap()
    if (ownership.remove(localListId) != null) saveListOwnership(ownership)
  }

  private fun saveListOwnership(ownership: Map<Long, Long>) {
    val encoded = ownership
      .map { (localListId, remoteListId) -> encodeFloppyListOwnership(localListId, remoteListId) }
      .toSet()
    preferences.edit().putStringSet(KEY_FLOPPY_LISTS_OWNERSHIP, encoded).apply()
  }

  private fun getSyncConfig(): Pair<FloppyConfig, String> {
    val config = floppySource.getConfig()
    if (!config.enabled || config.apiKey.isBlank()) throw IOException("Floppy list sync is not configured")
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

  private suspend fun executeRequest(request: Request): FloppyListHttpResponse =
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
                continuation.resume(FloppyListHttpResponse(it.code, it.body.string()))
              }
            }
          }
        },
      )
    }

  private fun FloppyWatchlistType.mediaTypePath() =
    when (this) {
      FloppyWatchlistType.MOVIES -> "movie"
      FloppyWatchlistType.SHOWS -> "tv"
    }
}

internal fun encodeFloppyListOwnership(
  localListId: Long,
  remoteListId: Long,
): String = "$localListId:$remoteListId"

internal fun decodeFloppyListOwnership(value: String): Pair<Long, Long>? {
  val parts = value.split(':', limit = 2)
  if (parts.size != 2) return null
  val localListId = parts[0].toLongOrNull() ?: return null
  val remoteListId = parts[1].toLongOrNull() ?: return null
  if (localListId <= 0 || remoteListId <= 0) return null
  return localListId to remoteListId
}
