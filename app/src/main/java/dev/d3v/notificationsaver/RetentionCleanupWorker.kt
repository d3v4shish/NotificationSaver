package dev.d3v.notificationsaver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NotificationSaverApp
        return runCatching {
            app.repository.runRetentionCleanupNow()
            Result.success()
        }.getOrElse { error ->
            app.logger.error("RetentionCleanupWorker", "Scheduled retention cleanup failed", error)
            Result.retry()
        }
    }
}

object RetentionScheduler {
    private const val UNIQUE_WORK_NAME = "notification-history-retention"

    fun sync(context: Context, retentionDays: Int) {
        val workManager = WorkManager.getInstance(context)
        if (retentionDays <= 0) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
