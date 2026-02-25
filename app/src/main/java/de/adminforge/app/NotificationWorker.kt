package de.adminforge.app

import android.content.Context
import android.os.Build
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "AdminForgePollingHighFreq"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // For a 5-10 min interval, we use a OneTimeWorkRequest that reschedules itself.
            // 15 min is the minimum for PeriodicWorkRequest.
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.MINUTES) // Start polling in 5 minutes
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            102,
            NotificationHelper.createExpeditedNotification(applicationContext),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", false)) return Result.success()

        // Fallback Logic: If UnifiedPush is active, we don't need high-freq periodic polling to save battery.
        // However, if we aren't using Push, we want it fast (5-10 min).
        val hasPush = prefs.getString("unified_push_endpoint", null) != null
        
        try {
            val newsOk = NewsPoller.performFetchSuspend(applicationContext)
            val statusOk = StatusPoller.performFetchSuspend(applicationContext)

            if (newsOk) checkNews(prefs)
            if (statusOk) checkStatus(prefs)

            if (!newsOk || !statusOk) {
                return Result.retry()
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        // Schedule next execution if not triggered by push and notifications are still enabled
        if (!hasPush && prefs.getBoolean("notifications_enabled", false)) {
            val nextDelay = if (runAttemptCount > 0) 10L else 5L // Jitter or adjust based on success
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val nextRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(nextDelay, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                nextRequest
            )
        }

        return Result.success()
    }

    private fun checkNews(prefs: android.content.SharedPreferences) {
        val cachedNews = prefs.getString("cached_news", null) ?: return
        try {
            val items: List<NewsItem> = Gson().fromJson(cachedNews, object : TypeToken<List<NewsItem>>() {}.type)
            if (items.isNotEmpty()) {
                val firstItem = items.first()
                val lastNotifiedLink = prefs.getString("bg_last_notified_news", "")
                if (firstItem.link.isNotEmpty() && firstItem.link != lastNotifiedLink) {
                    showNotification(100, "Neuer Artikel", firstItem.title, "news")
                    prefs.edit().putString("bg_last_notified_news", firstItem.link).apply()
                }
            }
        } catch (e: Exception) {}
    }

    private fun checkStatus(prefs: android.content.SharedPreferences) {
        val offlineCount = StatusPoller.getOfflineCount()
        val lastNotifiedOfflineCount = prefs.getInt("bg_last_notified_offline_count", 0)

        if (offlineCount > 0 && offlineCount != lastNotifiedOfflineCount) {
            val title = if (offlineCount == 1) "adminForge Störung" else "adminForge Störungen"
            val message = if (offlineCount == 1) "1 Dienst ist offline!" else "$offlineCount Dienste sind offline!"
            showNotification(101, title, message, "status")
            
            prefs.edit().putInt("bg_last_notified_offline_count", offlineCount).apply()
        } else if (offlineCount == 0 && lastNotifiedOfflineCount > 0) {
            showNotification(101, "adminForge Entwarnung", "Alle Systeme sind wieder online!", "status")
            prefs.edit().putInt("bg_last_notified_offline_count", 0).apply()
        }
    }

    private fun showNotification(id: Int, title: String, message: String, target: String) {
        val targetActivity = if (target == "status") StatusActivity::class.java else NewsActivity::class.java
        NotificationHelper.showNotification(applicationContext, title, message, targetActivity, id)
    }
}
