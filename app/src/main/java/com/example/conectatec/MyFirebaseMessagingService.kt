package com.example.conectatec

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import com.example.conectatec.R.color.orange
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_SERVICE"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")
        Log.d(TAG, "Message notification: ${remoteMessage.notification}")

        // Crear un objeto de notificación para nuestro repositorio local
        var mensaje: String? = null
        var tipo: String? = "info"
        var fecha: String = "Ahora"

        // Procesar datos (prioridad mayor para mensajes de datos personalizados)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            tipo = remoteMessage.data["tipo"] ?: tipo
            mensaje = remoteMessage.data["mensaje"]
            fecha = remoteMessage.data["fecha"] ?: fecha
        }

        // Si no hay mensaje en datos, usar el mensaje de notificación como respaldo
        if (mensaje == null && remoteMessage.notification != null) {
            mensaje = remoteMessage.notification!!.body
        }

        // Si tenemos un mensaje para mostrar, procesarlo
        if (mensaje != null) {
            val notification = Notification(tipo, mensaje, fecha)

            // Guardar en repositorio
            NotificationRepository.addNotification(notification)

            // Siempre mostrar notificación del sistema (en segundo plano)
            // pero en primer plano también actualizar UI
            showSystemNotification(notification)

            if (isAppInForeground()) {
                // Actualizar UI si la app está en primer plano
                sendBroadcast(Intent("NEW_NOTIFICATION"))
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Token refreshed: $token")

        // Guardarlo en SharedPreferences para uso futuro
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()

        // Aquí deberías enviar el token a tu servidor para actualización
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // Implementa la lógica para enviar el token a tu servidor
        Log.d(TAG, "Sending token to server: $token")
        // TODO: Implementar API call para enviar token al servidor
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
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_tren_round)
            .setColor(10428993)
            .setContentTitle(getTitleByType(notification.tipo))
            .setContentText(notification.mensaje ?: "Tienes una nueva notificación")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 250, 250, 250))
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

    private fun getTitleByType(tipo: String?): String {
        return when (tipo?.lowercase()) {
            "alerta" -> "⚠️ ALERTA"
            "retraso" -> "⏱️ RETRASO"
            "info" -> "INFORMACIÓN"
            "estacion" -> "ESTACIÓN"
            "afluencia" -> "AFLUENCIA"
            "horario" -> "HORARIO"
            else -> "CONECTATEC"
        }
    }
}