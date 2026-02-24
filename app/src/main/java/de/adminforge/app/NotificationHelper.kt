package de.adminforge.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "adminforge_alerts"
    private const val CHANNEL_NAME = "AdminForge Alarme"
    private const val CHANNEL_DESC = "Wichtige Benachrichtigungen über Services und News"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, title: String, message: String, targetActivity: Class<*> = MainActivity::class.java, notificationId: Int = System.currentTimeMillis().toInt()) {
        val intent = Intent(context, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_custom)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        builder.setSmallIcon(R.drawable.ic_notification_af)
        builder.setLargeIcon(largeIcon)
        builder.setContentTitle(title)
        builder.setContentText(message)
        builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        builder.setContentIntent(pendingIntent)
        builder.setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }

    fun createExpeditedNotification(context: Context): android.app.Notification {
        createNotificationChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_af)
            .setContentTitle("adminForge Dienst")
            .setContentText("Hintergrund-Synchronisierung...")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Minimal noise for foreground task
            .build()
    }
}
