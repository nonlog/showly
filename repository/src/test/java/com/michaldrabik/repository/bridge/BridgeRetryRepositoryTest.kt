package com.michaldrabik.repository.bridge

import com.michaldrabik.data_local.database.model.BridgeRetryState
import com.michaldrabik.data_local.sources.BridgeRetryStatesLocalDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeRetryRepositoryTest {

  @Test
  fun `enqueue creates durable domain entry`() =
    runTest {
      val source = FakeBridgeRetryStatesLocalDataSource()
      val repository = BridgeRetryRepository(source)

      repository.enqueue("history", "IOException")

      val state = source.get("history")
      assertEquals("history", state?.domain)
      assertEquals(0, state?.attemptCount)
      assertEquals("IOException", state?.lastError)
    }

  @Test
  fun `duplicate enqueue preserves attempts and queue time`() =
    runTest {
      val source = FakeBridgeRetryStatesLocalDataSource().apply {
        upsert(
          BridgeRetryState(
            domain = "ratings",
            queuedAt = 100,
            attemptCount = 2,
            lastAttemptAt = 200,
            lastError = "OldError",
          ),
        )
      }
      val repository = BridgeRetryRepository(source)

      repository.enqueue("ratings", "NewError")

      val state = source.get("ratings")
      assertEquals(100L, state?.queuedAt)
      assertEquals(2, state?.attemptCount)
      assertEquals(200L, state?.lastAttemptAt)
      assertEquals("NewError", state?.lastError)
    }

  @Test
  fun `mark attempt increments durable attempt count`() =
    runTest {
      val source = FakeBridgeRetryStatesLocalDataSource().apply {
        upsert(BridgeRetryState(domain = "watchlist", queuedAt = 100))
      }
      val repository = BridgeRetryRepository(source)

      repository.markAttempt("watchlist")

      assertEquals(1, source.get("watchlist")?.attemptCount)
      assertEquals(true, (source.get("watchlist")?.lastAttemptAt ?: 0) > 0)
    }

  @Test
  fun `complete removes only the completed domain`() =
    runTest {
      val source = FakeBridgeRetryStatesLocalDataSource().apply {
        upsert(BridgeRetryState(domain = "history", queuedAt = 100))
        upsert(BridgeRetryState(domain = "lists", queuedAt = 200))
      }
      val repository = BridgeRetryRepository(source)

      repository.complete("history")

      assertNull(source.get("history"))
      assertEquals("lists", source.getAll().single().domain)
    }

  private class FakeBridgeRetryStatesLocalDataSource : BridgeRetryStatesLocalDataSource {
    private val states = linkedMapOf<String, BridgeRetryState>()

    override suspend fun get(domain: String): BridgeRetryState? = states[domain]

    override suspend fun getAll(): List<BridgeRetryState> = states.values.sortedBy { it.queuedAt }

    override suspend fun upsert(state: BridgeRetryState) {
      states[state.domain] = state
    }

    override suspend fun deleteDomain(domain: String) {
      states.remove(domain)
    }

    override suspend fun deleteAll() {
      states.clear()
    }
  }
}
