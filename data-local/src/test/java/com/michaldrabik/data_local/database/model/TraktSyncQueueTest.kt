package com.michaldrabik.data_local.database.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TraktSyncQueueTest {

  @Test
  fun `new local mutation starts unacknowledged by both providers`() {
    val item = TraktSyncQueue.createMovie(
      idTrakt = 42,
      createdAt = 100,
      updatedAt = 100,
    )

    assertFalse(item.traktDone)
    assertFalse(item.floppyDone)
  }

  @Test
  fun `remove mutation keeps provider acknowledgements pending`() {
    val item = TraktSyncQueue
      .createMovieWatchlist(idTrakt = 42, createdAt = 100, updatedAt = 200)
      .copy(operation = TraktSyncQueue.Operation.REMOVE.slug)

    assertEquals(TraktSyncQueue.Operation.REMOVE.slug, item.operation)
    assertFalse(item.traktDone)
    assertFalse(item.floppyDone)
  }

  @Test
  fun `episode mutation can carry durable parent identity`() {
    val item = TraktSyncQueue
      .createEpisode(
        episodeTraktId = 6558466,
        showTraktId = 154574,
        createdAt = 100,
        updatedAt = 200,
        clearProgress = false,
      ).copy(
        mediaTmdbId = 94997,
        seasonNumber = 1,
        episodeNumber = 2,
      )

    assertEquals(94997L, item.mediaTmdbId)
    assertEquals(1, item.seasonNumber)
    assertEquals(2, item.episodeNumber)
    assertFalse(item.traktDone)
    assertFalse(item.floppyDone)
  }

  @Test
  fun `legacy queue factories keep optional provider identity empty`() {
    val item = TraktSyncQueue.createMovie(
      idTrakt = 42,
      createdAt = 100,
      updatedAt = 100,
    )

    assertNull(item.mediaTmdbId)
    assertNull(item.seasonNumber)
    assertNull(item.episodeNumber)
  }
}
