package com.michaldrabik.repository.bridge

import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.database.model.BridgeRetryState
import com.michaldrabik.data_local.sources.BridgeRetryStatesLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeRetryRepository @Inject constructor(
  private val localSource: BridgeRetryStatesLocalDataSource,
) {
  suspend fun getAll(): List<BridgeRetryState> = localSource.getAll()

  suspend fun enqueue(
    domain: String,
    error: String? = null,
  ) {
    require(domain.isNotBlank()) { "Bridge retry domain must not be blank" }
    val existing = localSource.get(domain)
    localSource.upsert(
      existing?.copy(lastError = error ?: existing.lastError)
        ?: BridgeRetryState(
          domain = domain,
          queuedAt = nowUtcMillis(),
          lastError = error,
        ),
    )
  }

  suspend fun markAttempt(domain: String) {
    val existing = localSource.get(domain) ?: return
    localSource.upsert(
      existing.copy(
        attemptCount = existing.attemptCount + 1,
        lastAttemptAt = nowUtcMillis(),
      ),
    )
  }

  suspend fun markFailure(
    domain: String,
    error: String,
  ) {
    val existing = localSource.get(domain)
    if (existing == null) {
      enqueue(domain, error)
    } else {
      localSource.upsert(existing.copy(lastError = error))
    }
  }

  suspend fun complete(domain: String) = localSource.deleteDomain(domain)

  suspend fun clearAll() = localSource.deleteAll()
}
