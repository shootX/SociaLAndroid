package com.ich.socialsave.account

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ich.socialsave.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Runs one sync (see [SyncEngine]) when the network is available; retries with exponential backoff. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (applicationContext.container.account.sync()) {
        SyncOutcome.SUCCESS, SyncOutcome.SKIPPED, SyncOutcome.EXPIRED -> Result.success()
        SyncOutcome.LATER -> {
            applicationContext.container.sync.followUp()
            Result.success()
        }
        SyncOutcome.RETRY -> Result.retry()
        SyncOutcome.FAILED -> Result.failure()
    }
}

/**
 * Sync triggers: sign-in, app foreground, 2 s after the last library write, share/clipboard saves and
 * "Sync now". All of them end in one unique WorkManager job.
 */
class SyncScheduler(
    context: Context,
    private val scope: CoroutineScope,
    private val canSync: () -> Boolean,
) {
    private val app = context.applicationContext
    private var debounce: Job? = null

    /** Runs a sync soon; if one is already waiting to start it covers this request. */
    fun requestSync() {
        if (!canSync()) return
        scope.launch(Dispatchers.IO) {
            val infos = runCatching {
                WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).first()
            }.getOrDefault(emptyList())
            val waiting = infos.filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            when {
                // Not started yet: it will see this change.
                waiting.any { it.runAttemptCount == 0 } -> Unit
                // A retry waiting out its backoff: try again now instead.
                waiting.isNotEmpty() -> enqueue(ExistingWorkPolicy.REPLACE)
                // Idle, or running and maybe already past this change: queue one more run after it.
                else -> enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
    }

    /** "Sync now" and sign-in: start right away, even if a retry is waiting out its backoff. */
    fun syncNow() {
        if (!canSync()) return
        debounce?.cancel()
        enqueue(ExistingWorkPolicy.REPLACE)
    }

    /** Called after every library write: syncs 2 s after the last one. */
    fun scheduleDebounced() {
        if (!canSync()) return
        debounce?.cancel()
        debounce = scope.launch {
            delay(DEBOUNCE_MILLIS)
            requestSync()
        }
    }

    /** The server held its cursor: one more run after this one, at least 3 s later (contract note). */
    fun followUp() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE, delaySeconds = FOLLOW_UP_DELAY_SECONDS)
    }

    fun cancel() {
        debounce?.cancel()
        WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_NAME)
    }

    private fun enqueue(policy: ExistingWorkPolicy, delaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    private companion object {
        const val UNIQUE_NAME = "sync"
        const val DEBOUNCE_MILLIS = 2_000L
        const val FOLLOW_UP_DELAY_SECONDS = 5L
    }
}
