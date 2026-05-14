package com.project.gramaurja

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Create the notification when a message arrives from Firebase Cloud
        val title = remoteMessage.notification?.title ?: "Grama-Urja Alert"
        val body = remoteMessage.notification?.body ?: "Power Status Updated!"

        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // This is where you would send the device token to your backend if needed
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "power_alerts"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Fix for API level 24: Only create Channel for Android 8.0 (API 26) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Power Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Uses default foreground icon
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensures visibility on older devices
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}