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
import java.time.OffsetDateTime
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

data class FloppyBridgeList(
  val id: Long,
  val name: String,
  val description: String,
  val isPublic: Boolean,
  val latestUpdate: Long,
)

internal data class FloppyListWire(
  val id: Long,
  val name: String,
  val description: String = "",
  @Json(name = "is_public") val isPublic: Boolean = false,
  @Json(name = "latest_update") val latestUpdate: String? = null,
)

internal data class FloppyListEnvelope(
  val pagination: FloppyBridgePagination = FloppyBridgePagination(),
  val results: List<FloppyListWire> = emptyList(),
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

  fun getOwnedListMappings(): Map<Long, Long>

  fun bindOwnedList(
    localListId: Long,
    remoteListId: Long,
  )

  suspend fun fetchLists(): List<FloppyBridgeList>

  suspend fun fetchListItems(remoteListId: Long): Set<FloppyListItemRef>

  suspend fun ensureOwnedList(
    localListId: Long,
    name: String,
    description: String?,
    isPublic: Boolean,
  ): Boolean

  fun releaseOwnedList(localListId: Long): Boolean

  suspend fun deleteOwnedList(localListId: Long): Boolean

  suspend fun ensureListItem(
    localListId: Long,
    item: FloppyListItemRef,
  ): Boolean

  suspend fun removeListItem(
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
  private val listEnvelopeAdapter = moshi.adapter(FloppyListEnvelope::class.java)
  private val trackedEnvelopeAdapter = moshi.adapter(FloppyBridgeTrackedEnvelope::class.java)

  override fun getOwnedLocalListIds(): Set<Long> = getListOwnership().keys

  override fun getOwnedListMappings(): Map<Long, Long> = getListOwnership()

  override fun bindOwnedList(
    localListId: Long,
    remoteListId: Long,
  ) {
    require(localListId > 0) { "Local list id must be positive" }
    require(remoteListId > 0) { "Remote list id must be positive" }
    val ownership = getListOwnership().toMutableMap()
    ownership.entries.removeAll { (ownedLocalId, ownedRemoteId) ->
      ownedLocalId != localListId && ownedRemoteId == remoteListId
    }
    ownership[localListId] = remoteListId
    saveListOwnership(ownership)
  }

  override suspend fun fetchLists(): List<FloppyBridgeList> {
    val (config, baseUrl) = getSyncConfig()
    val results = mutableListOf<FloppyBridgeList>()
    var offset = 0
    while (true) {
      val response = executeRequest(
        authenticatedRequestBuilder(
          "$baseUrl/api/v1/lists/?limit=200&offset=$offset",
          config.apiKey,
        ).get().build(),
      )
      if (response.code !in 200..299) {
        throw IOException("Floppy lists fetch failed with HTTP " + response.code)
      }
      val envelope = try {
        listEnvelopeAdapter.fromJson(response.body)
      } catch (error: Exception) {
        throw IOException("Unable to parse Floppy lists response", error)
      } ?: throw IOException("Floppy lists response was empty")
      envelope.results.mapTo(results) { wire ->
        FloppyBridgeList(
          id = wire.id,
          name = wire.name,
          description = wire.description,
          isPublic = wire.isPublic,
          latestUpdate = wire.latestUpdate.toEpochMillisOrZeroForList(),
        )
      }
      val nextOffset = offset + envelope.results.size
      if (envelope.results.isEmpty() || nextOffset >= envelope.pagination.total) break
      offset = nextOffset
    }
    return results
  }

  override suspend fun fetchListItems(remoteListId: Long): Set<FloppyListItemRef> {
    require(remoteListId > 0) { "Remote list id must be positive" }
    val (config, baseUrl) = getSyncConfig()
    val results = linkedSetOf<FloppyListItemRef>()
    var offset = 0
    while (true) {
      val response = executeRequest(
        authenticatedRequestBuilder(
          "$baseUrl/api/v1/lists/$remoteListId/items/?limit=200&offset=$offset",
          config.apiKey,
        ).get().build(),
      )
      if (response.code == 404) return emptySet()
      if (response.code !in 200..299) {
        throw IOException("Floppy list items fetch failed with HTTP " + response.code)
      }
      val envelope = try {
        trackedEnvelopeAdapter.fromJson(response.body)
      } catch (error: Exception) {
        throw IOException("Unable to parse Floppy list items response", error)
      } ?: throw IOException("Floppy list items response was empty")
      envelope.results.mapNotNullTo(results) { wire ->
        val item = wire.item ?: return@mapNotNullTo null
        if (item.source != "tmdb") return@mapNotNullTo null
        val tmdbId = item.mediaId?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNullTo null
        val type = when (item.mediaType) {
          "movie" -> FloppyWatchlistType.MOVIES
          "tv" -> FloppyWatchlistType.SHOWS
          else -> return@mapNotNullTo null
        }
        FloppyListItemRef(type, tmdbId)
      }
      val nextOffset = offset + envelope.results.size
      if (envelope.results.isEmpty() || nextOffset >= envelope.pagination.total) break
      offset = nextOffset
    }
    return results
  }

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

  override suspend fun deleteOwnedList(localListId: Long): Boolean {
    val remoteListId = getListOwnership()[localListId] ?: return false
    val (config, baseUrl) = getSyncConfig()
    val response = executeRequest(
      authenticatedRequestBuilder("$baseUrl/api/v1/lists/$remoteListId/", config.apiKey)
        .delete()
        .build(),
    )
    return when (response.code) {
      204 -> {
        clearListOwnership(localListId)
        true
      }
      404 -> {
        clearListOwnership(localListId)
        false
      }
      else -> throw IOException("Floppy list deletion failed with HTTP " + response.code)
    }
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

  override suspend fun removeListItem(
    localListId: Long,
    item: FloppyListItemRef,
  ): Boolean {
    val remoteListId = getListOwnership()[localListId] ?: return false
    val (config, baseUrl) = getSyncConfig()
    val response = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/${item.type.mediaTypePath()}/tmdb/${item.tmdbId}/lists/$remoteListId/",
        config.apiKey,
      ).delete().build(),
    )
    return when (response.code) {
      204 -> true
      404 -> false
      else -> throw IOException("Floppy list item removal failed with HTTP " + response.code)
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

private fun String?.toEpochMillisOrZeroForList(): Long {
  if (this.isNullOrBlank()) return 0
  return try {
    OffsetDateTime.parse(this).toInstant().toEpochMilli()
  } catch (_: Exception) {
    0
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
