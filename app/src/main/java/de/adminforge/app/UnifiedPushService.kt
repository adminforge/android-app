package de.adminforge.app

import android.content.Context
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import android.widget.Toast

class UnifiedPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        // Trigger background update check via expedited work
        NotificationWorker.runOnce(applicationContext)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val prefs = applicationContext.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("unified_push_endpoint", endpoint.url)
            .apply()

        // Log for transparency
        android.util.Log.d("UnifiedPush", "New endpoint registered: ${endpoint.url}")

        // Notify UI components if active
        val intent = android.content.Intent("de.adminforge.app.UNIFIED_PUSH_UPDATE")
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(intent)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val prefs = applicationContext.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("unified_push_endpoint").apply()
        
        android.util.Log.e("UnifiedPush", "Registration failed: $reason")
        
        // Don't toast in background, but ensure logs are clear
    }

    override fun onUnregistered(instance: String) {
        applicationContext.getSharedPreferences("adminforge_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("unified_push_endpoint")
            .apply()
        
        android.util.Log.d("UnifiedPush", "Unregistered from push service")
    }
}
