package com.forge.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Syncs in the background: when Forge opens or closes, and every hour while signed in. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Account.user(applicationContext) == null) return Result.success()
        return try {
            SyncEngine.sync(applicationContext)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val ONCE = "forge_sync_now"
        private const val PERIODIC = "forge_sync_hourly"
        private const val MIN_GAP_MS = 60_000L

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** A sync soon, unless one ran in the last minute. */
        fun kick(c: Context, force: Boolean = false) {
            val app = c.applicationContext
            if (!Account.configured || Account.user(app) == null) return
            if (!force && System.currentTimeMillis() - SyncEngine.lastSync(app) < MIN_GAP_MS) return
            WorkManager.getInstance(app).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build()
            )
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(online).build()
            )
        }

        fun stop(c: Context) {
            WorkManager.getInstance(c.applicationContext).cancelUniqueWork(PERIODIC)
        }
    }
}
