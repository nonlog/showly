package com.michaldrabik.data_remote.floppy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloppyRemoteDataSourceTest {

  @Test
  fun `normalizes base URL`() {
    assertEquals(
      "https://tracker.example.com/floppy",
      normalizeFloppyBaseUrl("  https://tracker.example.com/floppy///  "),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects non http URL`() {
    normalizeFloppyBaseUrl("ftp://tracker.example.com")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects embedded credentials`() {
    normalizeFloppyBaseUrl("https://user:password@tracker.example.com")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects query parameters`() {
    normalizeFloppyBaseUrl("https://tracker.example.com?token=secret")
  }

  @Test
  fun `matches equivalent watched instants`() {
    assertTrue(
      sameFloppyInstant(
        "2026-09-03T10:00:00Z",
        "2026-09-03T18:00:00+08:00",
      ),
    )
  }

  @Test
  fun `does not match different watched instants`() {
    assertFalse(
      sameFloppyInstant(
        "2026-09-03T10:00:00Z",
        "2026-09-03T10:00:01Z",
      ),
    )
  }

  @Test
  fun `detects Floppy identity changes but ignores enabled toggle`() {
    val previous = FloppyConfig(enabled = true, baseUrl = "https://tracker.example.com", apiKey = "token-a")

    assertFalse(isFloppyIdentityChanged(previous, previous.copy(enabled = false)))
    assertTrue(isFloppyIdentityChanged(previous, previous.copy(baseUrl = "https://other.example.com")))
    assertTrue(isFloppyIdentityChanged(previous, previous.copy(apiKey = "token-b")))
  }

  @Test
  fun `encodes and decodes watchlist ownership`() {
    val encoded = encodeFloppyWatchlistOwnership(tmdbId = 603, consumptionId = 42)

    assertEquals("603:42", encoded)
    assertEquals(603L to 42L, decodeFloppyWatchlistOwnership(encoded))
  }

  @Test
  fun `rejects invalid watchlist ownership records`() {
    assertEquals(null, decodeFloppyWatchlistOwnership(""))
    assertEquals(null, decodeFloppyWatchlistOwnership("603"))
    assertEquals(null, decodeFloppyWatchlistOwnership("-1:42"))
    assertEquals(null, decodeFloppyWatchlistOwnership("603:0"))
    assertEquals(null, decodeFloppyWatchlistOwnership("movie:42"))
  }

  @Test
  fun `creates score-only rating row when media has no consumption`() {
    assertEquals(
      FloppyRatingWriteAction.CREATE,
      resolveFloppyRatingWriteAction(hasConsumption = false, score = 7.0),
    )
  }

  @Test
  fun `rating removal without consumption is a no-op`() {
    assertEquals(
      FloppyRatingWriteAction.NOOP,
      resolveFloppyRatingWriteAction(hasConsumption = false, score = null),
    )
  }

  @Test
  fun `existing consumption uses rating patch`() {
    assertEquals(
      FloppyRatingWriteAction.PATCH,
      resolveFloppyRatingWriteAction(hasConsumption = true, score = 7.0),
    )
    assertEquals(
      FloppyRatingWriteAction.PATCH,
      resolveFloppyRatingWriteAction(hasConsumption = true, score = null),
    )
  }

  @Test
  fun `maps flat movie history entry without detail request`() {
    val event = FloppyBridgeFlatHistoryWire(
      mediaType = "movie",
      item = FloppyBridgeHistoryItemWire(mediaId = "334541", source = "tmdb", mediaType = "movie"),
      instanceId = 24,
      playedAtLocal = "2026-03-22T13:26:00+08:00",
    ).toHistoryEvent()

    assertEquals(FloppyBridgeHistoryKind.MOVIE, event?.identity?.kind)
    assertEquals(334541L, event?.identity?.tmdbId)
    assertEquals(24L, event?.consumptionId)
    assertEquals(1774157160000L, event?.watchedAt)
  }

  @Test
  fun `maps flat episode history entry using parent tmdb id and coordinates`() {
    val event = FloppyBridgeFlatHistoryWire(
      mediaType = "episode",
      item = FloppyBridgeHistoryItemWire(
        mediaId = "1399",
        source = "tmdb",
        mediaType = "episode",
        seasonNumber = 2,
        episodeNumber = 3,
      ),
      instanceId = 99,
      playedAtLocal = "2026-09-05T12:00:00+08:00",
    ).toHistoryEvent()

    assertEquals(FloppyBridgeHistoryKind.EPISODE, event?.identity?.kind)
    assertEquals(1399L, event?.identity?.tmdbId)
    assertEquals(2, event?.identity?.season)
    assertEquals(3, event?.identity?.episode)
    assertEquals(99L, event?.consumptionId)
  }
}
