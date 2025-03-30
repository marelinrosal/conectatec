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
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_SERVICE"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Registro detallado para depuración
        Log.d(TAG, "Message data: ${remoteMessage.data}")
        Log.d(TAG, "Message notification: ${remoteMessage.notification}")

        // Procesar mensaje de notificación (enviado desde la consola de Firebase)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification Message Body: ${notification.body}")

            // Crear objeto Notification con los datos de la notificación
            val notificationObj = Notification(
                tipo = "info", // Tipo predeterminado para notificaciones de consola
                mensaje = notification.body,
                fecha = "Ahora"
            )

            // Guardar en repositorio
            NotificationRepository.addNotification(notificationObj)

            // Mostrar notificación o actualizar UI
            if (isAppInForeground()) {
                sendBroadcast(Intent("NEW_NOTIFICATION"))
            } else {
                showSystemNotification(notificationObj)
            }
        }

        // Procesar mensaje de datos (para mensajes personalizados)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val tipo = remoteMessage.data["tipo"]
            val mensaje = remoteMessage.data["mensaje"]
            val fecha = remoteMessage.data["fecha"] ?: "Ahora"

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
        Log.d(TAG, "Token refreshed: $token")

        // También puedes guardarlo en SharedPreferences para uso futuro
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
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
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            .setContentText(notification.mensaje ?: "Tienes una nueva notificación")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificaciones de la app"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Usar un ID único o basado en algún identificador consistente
        val notificationId = notification.mensaje?.hashCode() ?: Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Notificación mostrada con ID: $notificationId")
    }
}