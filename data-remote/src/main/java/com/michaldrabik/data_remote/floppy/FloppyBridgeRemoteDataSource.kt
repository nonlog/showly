package com.michaldrabik.data_remote.floppy

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

enum class FloppyBridgeHistoryKind {
  MOVIE,
  EPISODE,
}

data class FloppyBridgeHistoryIdentity(
  val kind: FloppyBridgeHistoryKind,
  val tmdbId: Long,
  val season: Int? = null,
  val episode: Int? = null,
)

data class FloppyBridgeHistoryEvent(
  val identity: FloppyBridgeHistoryIdentity,
  val consumptionId: Long,
  val watchedAt: Long,
)

internal data class FloppyBridgeHistoryItemWire(
  @Json(name = "media_id") val mediaId: String? = null,
  val source: String? = null,
  @Json(name = "media_type") val mediaType: String? = null,
  @Json(name = "season_number") val seasonNumber: Int? = null,
  @Json(name = "episode_number") val episodeNumber: Int? = null,
)

internal data class FloppyBridgeFlatHistoryWire(
  @Json(name = "media_type") val mediaType: String? = null,
  val item: FloppyBridgeHistoryItemWire? = null,
)

internal data class FloppyBridgeFlatHistoryEnvelope(
  val pagination: FloppyBridgePagination = FloppyBridgePagination(),
  val results: List<FloppyBridgeFlatHistoryWire> = emptyList(),
)

internal data class FloppyBridgeConsumptionEnvelope(
  val pagination: FloppyBridgePagination = FloppyBridgePagination(),
  val results: List<FloppyConsumption> = emptyList(),
)

data class FloppyBridgeTrackedMedia(
  val consumptionId: Long,
  val tmdbId: Long,
  val status: Int?,
  val score: Double?,
  val createdAt: Long,
  val endDate: Long?,
)

data class FloppyBridgeRating(
  val tmdbId: Long,
  val score: Double?,
  val changedAt: Long,
)

internal data class FloppyBridgeRatingCreateRequest(
  val source: String = "tmdb",
  @Json(name = "media_id") val mediaId: Long,
  val status: Int? = null,
  val score: Double?,
)

internal data class FloppyBridgeRatingUpdateRequest(
  val score: Double?,
)

internal data class FloppyBridgeItem(
  @Json(name = "media_id") val mediaId: String? = null,
  val source: String? = null,
  @Json(name = "media_type") val mediaType: String? = null,
)

internal data class FloppyBridgeTrackedMediaWire(
  @Json(name = "consumption_id") val consumptionId: Long? = null,
  val item: FloppyBridgeItem? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  val score: Double? = null,
  val status: Int? = null,
  @Json(name = "end_date") val endDate: String? = null,
)

internal data class FloppyBridgePagination(
  val total: Int = 0,
  val limit: Int = 0,
  val offset: Int = 0,
)

internal data class FloppyBridgeTrackedEnvelope(
  val pagination: FloppyBridgePagination = FloppyBridgePagination(),
  val results: List<FloppyBridgeTrackedMediaWire> = emptyList(),
)

internal data class FloppyBridgeChange(
  val field: String,
  @Json(name = "new_value") val newValue: Any? = null,
)

internal data class FloppyBridgeChangeEntry(
  val timestamp: String? = null,
  val changes: List<FloppyBridgeChange> = emptyList(),
)

internal data class FloppyBridgeChangeEnvelope(
  val pagination: FloppyBridgePagination = FloppyBridgePagination(),
  val results: List<FloppyBridgeChangeEntry> = emptyList(),
)

private data class FloppyBridgeHttpResponse(
  val code: Int,
  val body: String,
)

interface FloppyBridgeRemoteDataSource {
  suspend fun fetchHistoryIdentities(): List<FloppyBridgeHistoryIdentity>

  suspend fun fetchHistoryEvents(identity: FloppyBridgeHistoryIdentity): List<FloppyBridgeHistoryEvent>

  suspend fun removeHistoryEvent(event: FloppyBridgeHistoryEvent): Boolean

  suspend fun fetchRatings(type: FloppyWatchlistType): List<FloppyBridgeRating>

  suspend fun fetchLatestRating(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): FloppyBridgeRating?

  suspend fun setRating(
    type: FloppyWatchlistType,
    tmdbId: Long,
    score: Double?,
  ): Boolean

  suspend fun fetchWatchlist(type: FloppyWatchlistType): List<FloppyBridgeTrackedMedia>

  suspend fun fetchLatestFieldChange(
    type: FloppyWatchlistType,
    tmdbId: Long,
    field: String,
  ): Long?

  suspend fun ensureWatchlistPresent(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): Boolean

  suspend fun removePlanning(
    type: FloppyWatchlistType,
    tmdbId: Long,
    consumptionId: Long,
  ): Boolean
}

@Singleton
internal class DefaultFloppyBridgeRemoteDataSource @Inject constructor(
  private val floppySource: FloppyRemoteDataSource,
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
  private val moshi: Moshi,
) : FloppyBridgeRemoteDataSource {

  companion object {
    private const val PAGE_LIMIT = 200
    private const val STATUS_PLANNING = 0
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }

  private val flatHistoryEnvelopeAdapter = moshi.adapter(FloppyBridgeFlatHistoryEnvelope::class.java)
  private val consumptionEnvelopeAdapter = moshi.adapter(FloppyBridgeConsumptionEnvelope::class.java)
  private val trackedEnvelopeAdapter = moshi.adapter(FloppyBridgeTrackedEnvelope::class.java)
  private val changeEnvelopeAdapter = moshi.adapter(FloppyBridgeChangeEnvelope::class.java)
  private val detailAdapter = moshi.adapter(FloppyMediaDetail::class.java)
  private val ratingCreateAdapter = moshi.adapter(FloppyBridgeRatingCreateRequest::class.java).serializeNulls()
  private val ratingUpdateAdapter = moshi.adapter(FloppyBridgeRatingUpdateRequest::class.java).serializeNulls()

  override suspend fun fetchHistoryIdentities(): List<FloppyBridgeHistoryIdentity> {
    val (config, baseUrl) = syncConfig()
    val identities = linkedSetOf<FloppyBridgeHistoryIdentity>()
    var offset = 0
    while (true) {
      val response = executeRequest(
        authenticatedRequestBuilder(
          "$baseUrl/api/v1/history/?flat=1&types=movie&types=episode&limit=$PAGE_LIMIT&offset=$offset",
          config.apiKey,
        ).get().build(),
      )
      if (response.code !in 200..299) {
        throw IOException("Floppy bridge history catalog failed with HTTP " + response.code)
      }
      val envelope = try {
        flatHistoryEnvelopeAdapter.fromJson(response.body)
      } catch (error: Exception) {
        throw IOException("Unable to parse Floppy history catalog", error)
      } ?: throw IOException("Floppy history catalog was empty")
      envelope.results.mapNotNullTo(identities) { it.toHistoryIdentity() }
      val nextOffset = offset + envelope.results.size
      if (envelope.results.isEmpty() || nextOffset >= envelope.pagination.total) break
      offset = nextOffset
    }
    return identities.toList()
  }

  override suspend fun fetchHistoryEvents(identity: FloppyBridgeHistoryIdentity): List<FloppyBridgeHistoryEvent> {
    val (config, baseUrl) = syncConfig()
    val results = mutableListOf<FloppyBridgeHistoryEvent>()
    var offset = 0
    while (true) {
      val path = when (identity.kind) {
        FloppyBridgeHistoryKind.MOVIE ->
          "$baseUrl/api/v1/media/movie/tmdb/${identity.tmdbId}/history/"
        FloppyBridgeHistoryKind.EPISODE -> {
          val season = requireNotNull(identity.season) { "Episode season is required" }
          val episode = requireNotNull(identity.episode) { "Episode number is required" }
          "$baseUrl/api/v1/media/tv/tmdb/${identity.tmdbId}/$season/$episode/history/"
        }
      }
      val response = executeRequest(
        authenticatedRequestBuilder("$path?limit=$PAGE_LIMIT&offset=$offset", config.apiKey).get().build(),
      )
      if (response.code == 404) return results
      if (response.code !in 200..299) {
        throw IOException("Floppy bridge history fetch failed with HTTP " + response.code)
      }
      val envelope = try {
        consumptionEnvelopeAdapter.fromJson(response.body)
      } catch (error: Exception) {
        throw IOException("Unable to parse Floppy consumption history", error)
      } ?: throw IOException("Floppy consumption history was empty")
      envelope.results.mapNotNullTo(results) { consumption ->
        val consumptionId = consumption.consumptionId ?: return@mapNotNullTo null
        consumption.endDate.toEpochMillisOrNull()?.let { watchedAt ->
          FloppyBridgeHistoryEvent(identity, consumptionId, watchedAt)
        }
      }
      val nextOffset = offset + envelope.results.size
      if (envelope.results.isEmpty() || nextOffset >= envelope.pagination.total) break
      offset = nextOffset
    }
    return results
  }

  override suspend fun fetchRatings(type: FloppyWatchlistType): List<FloppyBridgeRating> {
    val (config, baseUrl) = syncConfig()
    val mediaType = type.mediaTypePathForBridge()
    val candidates = mutableListOf<FloppyBridgeTrackedMedia>()
    var offset = 0
    while (true) {
      val response = executeRequest(
        authenticatedRequestBuilder(
          "$baseUrl/api/v1/media/$mediaType/?source=tmdb&rating=rated&limit=$PAGE_LIMIT&offset=$offset",
          config.apiKey,
        ).get().build(),
      )
      if (response.code !in 200..299) {
        throw IOException("Floppy bridge ratings fetch failed with HTTP " + response.code)
      }
      val envelope = parseTrackedEnvelope(response.body)
      envelope.results.mapNotNullTo(candidates) { wire -> wire.toTrackedMedia() }
      val nextOffset = offset + envelope.results.size
      if (envelope.results.isEmpty() || nextOffset >= envelope.pagination.total) break
      offset = nextOffset
    }

    return candidates
      .groupBy(FloppyBridgeTrackedMedia::tmdbId)
      .mapNotNull { (tmdbId, entries) ->
        fetchLatestRating(type, tmdbId) ?: entries
          .filter { it.score != null }
          .maxByOrNull(FloppyBridgeTrackedMedia::createdAt)
          ?.let { FloppyBridgeRating(tmdbId, it.score, it.createdAt) }
      }
  }

  override suspend fun fetchLatestRating(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): FloppyBridgeRating? {
    val entry = fetchLatestChangeEntry(type, tmdbId, "score") ?: return null
    val change = entry.changes.firstOrNull { it.field == "score" } ?: return null
    return FloppyBridgeRating(
      tmdbId = tmdbId,
      score = change.newValue.toDoubleOrNull(),
      changedAt = entry.timestamp.toEpochMillisOrZero(),
    )
  }

  override suspend fun setRating(
    type: FloppyWatchlistType,
    tmdbId: Long,
    score: Double?,
  ): Boolean {
    require(tmdbId > 0) { "TMDB id must be positive" }
    val (config, baseUrl) = syncConfig()
    val mediaType = type.mediaTypePathForBridge()
    val detailUrl = "$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/"
    val detail = executeRequest(authenticatedRequestBuilder(detailUrl, config.apiKey).get().build())

    if (detail.code == 404) {
      if (score == null) return false
      val body = ratingCreateAdapter
        .toJson(FloppyBridgeRatingCreateRequest(mediaId = tmdbId, score = score))
        .toRequestBody(JSON_MEDIA_TYPE)
      val response = executeRequest(
        authenticatedRequestBuilder("$baseUrl/api/v1/media/$mediaType/", config.apiKey)
          .post(body)
          .build(),
      )
      if (response.code !in 200..299) {
        throw IOException("Floppy bridge rating create failed with HTTP " + response.code)
      }
      return true
    }
    if (detail.code !in 200..299) {
      throw IOException("Floppy bridge rating detail failed with HTTP " + detail.code)
    }

    val body = ratingUpdateAdapter
      .toJson(FloppyBridgeRatingUpdateRequest(score))
      .toRequestBody(JSON_MEDIA_TYPE)
    val response = executeRequest(
      authenticatedRequestBuilder(detailUrl, config.apiKey)
        .patch(body)
        .build(),
    )
    if (response.code !in 200..299) {
      throw IOException("Floppy bridge rating update failed with HTTP " + response.code)
    }
    return true
  }

  override suspend fun removeHistoryEvent(event: FloppyBridgeHistoryEvent): Boolean {
    val (config, baseUrl) = syncConfig()
    val identity = event.identity
    val path = when (identity.kind) {
      FloppyBridgeHistoryKind.MOVIE ->
        "$baseUrl/api/v1/media/movie/tmdb/${identity.tmdbId}/history/${event.consumptionId}/"
      FloppyBridgeHistoryKind.EPISODE -> {
        val season = requireNotNull(identity.season) { "Episode season is required" }
        val episode = requireNotNull(identity.episode) { "Episode number is required" }
        "$baseUrl/api/v1/media/tv/tmdb/${identity.tmdbId}/$season/$episode/history/${event.consumptionId}/"
      }
    }
    val response = executeRequest(authenticatedRequestBuilder(path, config.apiKey).delete().build())
    return when (response.code) {
      204 -> true
      404 -> false
      else -> throw IOException("Floppy bridge history delete failed with HTTP " + response.code)
    }
  }

  override suspend fun fetchWatchlist(type: FloppyWatchlistType): List<FloppyBridgeTrackedMedia> {
    val (config, baseUrl) = syncConfig()
    val mediaType = type.mediaTypePathForBridge()
    val results = mutableListOf<FloppyBridgeTrackedMedia>()
    var offset = 0

    while (true) {
      val response = executeRequest(
        authenticatedRequestBuilder(
          "$baseUrl/api/v1/media/$mediaType/?source=tmdb&status=$STATUS_PLANNING&limit=$PAGE_LIMIT&offset=$offset",
          config.apiKey,
        ).get().build(),
      )
      if (response.code !in 200..299) {
        throw IOException("Floppy bridge watchlist fetch failed with HTTP " + response.code)
      }
      val envelope = parseTrackedEnvelope(response.body)
      val page = envelope.results
        .mapNotNull { it.toTrackedMedia() }
        .filter { it.status == STATUS_PLANNING }
      results += page

      val nextOffset = offset + envelope.results.size
      if (envelope.results.isEmpty() || nextOffset >= envelope.pagination.total) break
      offset = nextOffset
    }

    return results
  }

  override suspend fun fetchLatestFieldChange(
    type: FloppyWatchlistType,
    tmdbId: Long,
    field: String,
  ): Long? = fetchLatestChangeEntry(type, tmdbId, field)?.timestamp.toEpochMillisOrNull()

  override suspend fun ensureWatchlistPresent(
    type: FloppyWatchlistType,
    tmdbId: Long,
  ): Boolean = floppySource.ensureWatchlistPlanning(type, tmdbId)

  override suspend fun removePlanning(
    type: FloppyWatchlistType,
    tmdbId: Long,
    consumptionId: Long,
  ): Boolean {
    require(tmdbId > 0) { "TMDB id must be positive" }
    require(consumptionId > 0) { "Consumption id must be positive" }
    val (config, baseUrl) = syncConfig()
    val mediaType = type.mediaTypePathForBridge()
    val detailResponse = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/",
        config.apiKey,
      ).get().build(),
    )
    if (detailResponse.code == 404) return false
    if (detailResponse.code !in 200..299) {
      throw IOException("Floppy bridge watchlist detail failed with HTTP " + detailResponse.code)
    }
    val detail = try {
      detailAdapter.fromJson(detailResponse.body)
    } catch (error: Exception) {
      throw IOException("Unable to parse Floppy watchlist detail", error)
    } ?: throw IOException("Floppy watchlist detail was empty")
    val target = detail.consumptions.firstOrNull { it.consumptionId == consumptionId }
    if (target?.status != STATUS_PLANNING) return false

    val response = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/history/$consumptionId/",
        config.apiKey,
      ).delete().build(),
    )
    return when (response.code) {
      204 -> true
      404 -> false
      else -> throw IOException("Floppy bridge Planning delete failed with HTTP " + response.code)
    }
  }

  private suspend fun fetchLatestChangeEntry(
    type: FloppyWatchlistType,
    tmdbId: Long,
    field: String,
  ): FloppyBridgeChangeEntry? {
    require(tmdbId > 0) { "TMDB id must be positive" }
    val (config, baseUrl) = syncConfig()
    val mediaType = type.mediaTypePathForBridge()
    val response = executeRequest(
      authenticatedRequestBuilder(
        "$baseUrl/api/v1/media/$mediaType/tmdb/$tmdbId/changes_history/?limit=$PAGE_LIMIT&offset=0",
        config.apiKey,
      ).get().build(),
    )
    if (response.code == 404) return null
    if (response.code !in 200..299) {
      throw IOException("Floppy bridge changes-history fetch failed with HTTP " + response.code)
    }
    val envelope = try {
      changeEnvelopeAdapter.fromJson(response.body)
    } catch (error: Exception) {
      throw IOException("Unable to parse Floppy changes-history response", error)
    } ?: throw IOException("Floppy changes-history response was empty")
    return envelope.results
      .filter { entry -> entry.changes.any { it.field == field } }
      .maxByOrNull { it.timestamp.toEpochMillisOrZero() }
  }

  private fun FloppyBridgeTrackedMediaWire.toTrackedMedia(): FloppyBridgeTrackedMedia? {
    val consumptionId = consumptionId ?: return null
    val item = item ?: return null
    if (item.source != "tmdb") return null
    val tmdbId = item.mediaId?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    return FloppyBridgeTrackedMedia(
      consumptionId = consumptionId,
      tmdbId = tmdbId,
      status = status,
      score = score,
      createdAt = createdAt.toEpochMillisOrZero(),
      endDate = endDate.toEpochMillisOrNull(),
    )
  }

  private fun parseTrackedEnvelope(body: String): FloppyBridgeTrackedEnvelope =
    try {
      trackedEnvelopeAdapter.fromJson(body)
    } catch (error: Exception) {
      throw IOException("Unable to parse Floppy tracked-media response", error)
    } ?: throw IOException("Floppy tracked-media response was empty")

  private fun syncConfig(): Pair<FloppyConfig, String> {
    val config = floppySource.getConfig()
    if (!config.enabled || config.apiKey.isBlank()) throw IOException("Floppy bridge is not configured")
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

  private suspend fun executeRequest(request: Request): FloppyBridgeHttpResponse =
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
                continuation.resume(FloppyBridgeHttpResponse(it.code, it.body.string()))
              }
            }
          }
        },
      )
    }
}

private fun FloppyBridgeFlatHistoryWire.toHistoryIdentity(): FloppyBridgeHistoryIdentity? {
  val item = item ?: return null
  if (item.source != "tmdb") return null
  val tmdbId = item.mediaId?.toLongOrNull()?.takeIf { it > 0 } ?: return null
  return when (mediaType ?: item.mediaType) {
    "movie" -> FloppyBridgeHistoryIdentity(FloppyBridgeHistoryKind.MOVIE, tmdbId)
    "episode" -> {
      val season = item.seasonNumber ?: return null
      val episode = item.episodeNumber ?: return null
      FloppyBridgeHistoryIdentity(FloppyBridgeHistoryKind.EPISODE, tmdbId, season, episode)
    }
    else -> null
  }
}

private fun Any?.toDoubleOrNull(): Double? =
  when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
  }

private fun String?.toEpochMillisOrNull(): Long? {
  if (this.isNullOrBlank()) return null
  return try {
    OffsetDateTime.parse(this).toInstant().toEpochMilli()
  } catch (_: Exception) {
    null
  }
}

private fun String?.toEpochMillisOrZero(): Long = toEpochMillisOrNull() ?: 0L

private fun FloppyWatchlistType.mediaTypePathForBridge() =
  when (this) {
    FloppyWatchlistType.MOVIES -> "movie"
    FloppyWatchlistType.SHOWS -> "tv"
  }
