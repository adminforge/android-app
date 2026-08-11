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

            // KEEP, not REPLACE: this runs on every app cold start while notifications are
            // enabled. Resetting an already-scheduled/running chain here means a user who
            // reopens the app more often than the poll interval would never let a background
            // poll actually fire.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
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
        } catch (e: Exception) {
            // Fall through and reschedule the next cycle below at the normal cadence, rather
            // than returning Result.retry(): that used to skip the reschedule entirely and
            // hand cadence control to WorkManager's own exponential backoff for this request,
            // which can grow the interval to hours - degrading status notifications too, even
            // if only the unrelated news feed was failing.
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

            // APPEND_OR_REPLACE, not REPLACE: this call runs from inside the very work item
            // it's about to supersede. REPLACE cancels the "existing" work under this name -
            // which is this currently-running invocation - racing its own Result.success()
            // returned just below.
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
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
