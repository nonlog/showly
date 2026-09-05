package com.michaldrabik.ui_base.floppy

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.bridge.BridgeRetryRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.trakt.TraktSyncWorker
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named

@HiltWorker
class FloppyBridgeRetryWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted workerParams: WorkerParameters,
  private val historyRunner: FloppyBridgeHistoryRunner,
  private val listsRunner: FloppyBridgeListsRunner,
  private val ratingsRunner: FloppyBridgeRatingsRunner,
  private val watchlistRunner: FloppyBridgeWatchlistRunner,
  private val retryRepository: BridgeRetryRepository,
  private val floppyRemoteDataSource: FloppyRemoteDataSource,
  private val userTraktManager: UserTraktManager,
  @Named("miscPreferences") private val miscPreferences: SharedPreferences,
) : CoroutineWorker(context, workerParams) {

  companion object {
    const val TAG_ID = "FLOPPY_BRIDGE_RETRY_WORK_ID"
    private const val UNIQUE_WORK = "FLOPPY_BRIDGE_RETRY_WORK"
    private const val MAX_AUTO_RETRIES = 4

    fun schedule(workManager: WorkManager) {
      val request = OneTimeWorkRequestBuilder<FloppyBridgeRetryWorker>()
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).setBackoffCriteria(
          BackoffPolicy.EXPONENTIAL,
          30,
          TimeUnit.SECONDS,
        ).addTag(TAG_ID)
        .build()
      workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(workManager: WorkManager) {
      workManager.cancelUniqueWork(UNIQUE_WORK)
    }
  }

  override suspend fun doWork(): Result =
    BridgeSyncExecutionGate.mutex.withLock {
      val pending = retryRepository.getAll()
      if (pending.isEmpty()) return@withLock Result.success()
      val config = floppyRemoteDataSource.getConfig()
      if (!config.enabled || !userTraktManager.isAuthorized()) {
        return@withLock Result.success()
      }

      miscPreferences
        .edit()
        .putLong(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_ATTEMPT, nowUtcMillis())
        .apply()

      var changes = 0
      val failures = linkedSetOf<String>()
      pending.forEach { retry ->
        retryRepository.markAttempt(retry.domain)
        try {
          changes += runDomain(retry.domain)
          retryRepository.complete(retry.domain)
        } catch (error: Throwable) {
          rethrowCancellation(error)
          failures += retry.domain
          val errorName = error.javaClass.simpleName.ifBlank { "BridgeError" }
          retryRepository.markFailure(retry.domain, errorName)
          Timber.w(error, "Floppy bridge retry failed for ${retry.domain}")
          Logger.record(error, "FloppyBridgeRetryWorker::${retry.domain}")
        }
      }

      if (failures.isEmpty()) {
        miscPreferences
          .edit()
          .putLong(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_SUCCESS, nowUtcMillis())
          .putInt(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_CHANGES, changes)
          .remove(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_FAILURES)
          .apply()
        return@withLock Result.success()
      }

      miscPreferences
        .edit()
        .putString(TraktSyncWorker.KEY_LAST_FLOPPY_BRIDGE_FAILURES, failures.joinToString(","))
        .apply()

      if (runAttemptCount + 1 >= MAX_AUTO_RETRIES) {
        Timber.w("Floppy bridge automatic retry limit reached; pending domains remain durable")
        Result.success()
      } else {
        Result.retry()
      }
    }

  private suspend fun runDomain(domain: String): Int =
    when (domain) {
      "history" -> historyRunner.run()
      "lists" -> listsRunner.run()
      "ratings" -> ratingsRunner.run()
      "watchlist" -> watchlistRunner.run()
      else -> {
        Timber.w("Dropping unknown Floppy bridge retry domain: $domain")
        0
      }
    }
}
