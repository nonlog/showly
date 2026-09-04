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
}
