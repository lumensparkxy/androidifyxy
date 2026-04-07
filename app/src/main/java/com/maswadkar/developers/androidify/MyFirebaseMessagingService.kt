package com.maswadkar.developers.androidify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service to handle:
 * - FCM token refresh
 * - Foreground notification display
 * - Topic subscription for broadcast notifications
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "krishi_notifications"
        private const val CHANNEL_NAME = "Krishi AI Notifications"
        private const val FCM_ANALYTICS_DATA_EXTRA = "gcm.n.analytics_data"

        // Topic for all users - use this in Firebase Console to send to all users
        const val TOPIC_ALL = "all"
        const val TOPIC_PROMOTIONS = "promotions"
        const val TOPIC_UPDATES = "updates"

        /**
         * Subscribe to default topics. Call this from MainActivity or Application class.
         */
        fun subscribeToDefaultTopics() {
            val messaging = FirebaseMessaging.getInstance()

            // Subscribe to "all" topic for broadcast notifications
            messaging.subscribeToTopic(TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to topic: $TOPIC_ALL")
                    } else {
                        Log.e(TAG, "Failed to subscribe to topic: $TOPIC_ALL", task.exception)
                    }
                }

            // Subscribe to updates topic for app updates
            messaging.subscribeToTopic(TOPIC_UPDATES)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to topic: $TOPIC_UPDATES")
                    } else {
                        Log.e(TAG, "Failed to subscribe to topic: $TOPIC_UPDATES", task.exception)
                    }
                }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM Token refreshed: $token")
        // Re-subscribe to topics when token is refreshed
        subscribeToDefaultTopics()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")
        val messageIntent = message.toIntent()

        // Handle notification payload (when app is in foreground)
        message.notification?.let { notification ->
            Log.d(TAG, "Notification Title: ${notification.title}")
            Log.d(TAG, "Notification Body: ${notification.body}")
            showNotification(message, messageIntent)
        }

        // Handle data payload if needed
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${message.data}")
            // Process data payload here if needed
        }
    }

    private fun showNotification(message: RemoteMessage, messageIntent: Intent) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val messageExtras = messageIntent.extras?.let(::Bundle)

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from Krishi AI"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open app when notification is tapped
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (messageExtras != null) {
                putExtras(messageExtras)
                if (getBundleExtra(FCM_ANALYTICS_DATA_EXTRA) == null) {
                    putExtra(FCM_ANALYTICS_DATA_EXTRA, Bundle(messageExtras))
                }
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(message.notification?.title ?: getString(R.string.app_name))
            .setContentText(message.notification?.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
