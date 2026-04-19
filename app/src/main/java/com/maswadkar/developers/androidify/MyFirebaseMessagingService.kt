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
import com.google.firebase.auth.FirebaseAuth
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
        private const val PREFS_NAME = "krishi_fcm_prefs"
        private const val KEY_LAST_USER_TOPIC = "last_user_topic"
        private const val USER_TOPIC_PREFIX = "user_"

        // Topic for all users - use this in Firebase Console to send to all users
        const val TOPIC_ALL = "all"
        const val TOPIC_PROMOTIONS = "promotions"
        const val TOPIC_UPDATES = "updates"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from Krishi AI"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

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

        fun syncUserTopic(context: Context, userId: String?) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val previousTopic = prefs.getString(KEY_LAST_USER_TOPIC, null)
            val nextTopic = buildUserTopic(userId)
            val messaging = FirebaseMessaging.getInstance()

            if (previousTopic != null && previousTopic != nextTopic) {
                messaging.unsubscribeFromTopic(previousTopic)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Unsubscribed from user topic: $previousTopic")
                        } else {
                            Log.e(TAG, "Failed to unsubscribe from user topic: $previousTopic", task.exception)
                        }
                    }
            }

            if (nextTopic != null && nextTopic != previousTopic) {
                messaging.subscribeToTopic(nextTopic)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Subscribed to user topic: $nextTopic")
                        } else {
                            Log.e(TAG, "Failed to subscribe to user topic: $nextTopic", task.exception)
                        }
                    }
            }

            prefs.edit().putString(KEY_LAST_USER_TOPIC, nextTopic).apply()
        }

        private fun buildUserTopic(userId: String?): String? {
            if (userId.isNullOrBlank()) return null
            val sanitized = userId.replace(Regex("[^A-Za-z0-9\\-_.~%]"), "_")
            return "$USER_TOPIC_PREFIX$sanitized"
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM Token refreshed: $token")
        // Re-subscribe to topics when token is refreshed
        subscribeToDefaultTopics()
        syncUserTopic(applicationContext, FirebaseAuth.getInstance().currentUser?.uid)
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

        ensureNotificationChannel(this)

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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
