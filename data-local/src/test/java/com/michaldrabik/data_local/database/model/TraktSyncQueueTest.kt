package com.michaldrabik.data_local.database.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
}
