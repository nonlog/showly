package com.michaldrabik.data_remote.floppy

import org.junit.Assert.assertEquals
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
}
