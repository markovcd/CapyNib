package io.github.markovcd.capynib.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.markovcd.capynib.CapynibApp
import io.github.markovcd.capynib.core.memos.SyncFailure
import io.github.markovcd.capynib.core.sync.SyncStatus
import java.util.concurrent.TimeUnit

/** Syncs in the background now and then, so the phone catches up even when the app is closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as CapynibApp).graph
        return when (val status = graph.scheduler.syncNow()) {
            is SyncStatus.Failed -> if (status.failure is SyncFailure.Unauthorized) Result.failure() else Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "capynib-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
