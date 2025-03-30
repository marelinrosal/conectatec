package com.example.conectatec

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random
import android.util.Log

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.data.let { data ->
            val tipo = data["tipo"]
            val mensaje = data["mensaje"]
            val fecha = data["fecha"]

            val notification = Notification(tipo, mensaje, fecha)
            NotificationRepository.addNotification(notification)

            if (isAppInForeground()) {
                // Actualizar UI si la app está en primer plano
                sendBroadcast(Intent("NEW_NOTIFICATION"))
            } else {
                showSystemNotification(notification)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Save token to your server if needed
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = activityManager.runningAppProcesses ?: return false
        val packageName = packageName

        return processes.any { process ->
            process.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    process.processName == packageName
        }
    }

    private fun showSystemNotification(notification: Notification) {
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("openFragment", "notificaciones")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "notificaciones_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Nueva notificación")
            .setContentText(notification.mensaje)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }
}