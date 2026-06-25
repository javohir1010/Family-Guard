package com.familyguard.app.services

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.model.AppUsageBatch
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager task that:
 * 1. Collects app usage stats from UsageStatsManager
 * 2. Uploads batch to backend every hour
 *
 * Runs even when app is in background.
 * Survives device reboots (persisted in WorkManager DB).
 */
@HiltWorker
class UsageStatsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: ApiService
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "usage_stats_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageStatsSyncWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val usages = collectUsageStats()
            if (usages.isEmpty()) return Result.success()

            apiService.uploadAppUsage(AppUsageBatch(usages = usages))
            Result.success()
        } catch (e: Exception) {
            // Retry on network error
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun collectUsageStats(): List<AppUsageBatch.Usage> {
        val usm = applicationContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

        // Get today's start timestamp
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            ?: return emptyList()

        val pm = applicationContext.packageManager

        return stats
            .filter { it.totalTimeInForeground > 10_000 } // > 10 seconds
            .filter { it.packageName != applicationContext.packageName } // exclude self
            .mapNotNull { stat ->
                val appName = try {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(stat.packageName, 0)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    stat.packageName
                }

                val lastUsedIso = if (stat.lastTimeUsed > 0) {
                    java.time.Instant.ofEpochMilli(stat.lastTimeUsed).toString()
                } else null

                AppUsageBatch.Usage(
                    packageName = stat.packageName,
                    appName = appName,
                    durationSeconds = stat.totalTimeInForeground / 1000,
                    date = today,
                    lastUsed = lastUsedIso
                )
            }
    }
}
