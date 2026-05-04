package com.example.textmemail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Nuevo mensaje"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "chat_messages"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mensajes de Chat",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Usamos el hash del título (remitente) como ID único para la notificación
        val notificationId = title.hashCode()

        // El requestCode también debe ser único para que los PendingIntents no se sobrescriban
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setGroup("chat_group_$title") // Agrupar mensajes de la misma persona
            .build()

        // Al usar notificationId (basado en el nombre), cada persona tendrá su propia notificación individual
        notificationManager.notify(notificationId, notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}
