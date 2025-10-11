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
// import com.example.conectatec.R.color.orange // No se usa directamente, pero el color se define en showSystemNotification
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

/**
 * Servicio que extiende [FirebaseMessagingService] para manejar la recepción de mensajes
 * de Firebase Cloud Messaging (FCM).
 * <p>
 * Este servicio es responsable de:
 * <ul>
 *   <li>Recibir mensajes push tanto cuando la aplicación está en primer plano como en segundo plano.</li>
 *   <li>Procesar los datos del mensaje (payload de datos y/o notificación).</li>
 *   <li>Guardar la información de la notificación en un [NotificationRepository] local.</li>
 *   <li>Mostrar una notificación del sistema al usuario.</li>
 *   <li>Si la aplicación está en primer plano, enviar un broadcast para que la UI pueda actualizarse.</li>
 *   <li>Manejar la actualización del token FCM, guardándolo en SharedPreferences y notificando al servidor.</li>
 * </ul>
 * </p>
 *
 * @property TAG Etiqueta para logging, con valor "FCM_SERVICE".
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see NotificationRepository
 * @see InicioActivity
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_SERVICE" // Etiqueta para logs

    /**
     * Se llama cuando se recibe un nuevo mensaje de FCM.
     * <p>
     * Este método se ejecuta en un hilo de fondo, por lo que las operaciones de larga duración
     * o que bloquean la UI deben manejarse adecuadamente (aunque en este caso, las operaciones
     * son relativamente rápidas).
     * </p>
     * <p>
     * Procesa el mensaje, extrae datos relevantes (tipo, mensaje, fecha),
     * guarda la notificación en [NotificationRepository], muestra una notificación del sistema
     * y, si la app está en primer plano, envía un broadcast para actualizar la UI.
     * </p>
     *
     * @param remoteMessage Objeto [RemoteMessage] que contiene los detalles del mensaje FCM.
     *                      Puede incluir un payload de datos y/o un payload de notificación.
     * @see NotificationRepository.addNotification
     * @see showSystemNotification
     * @see isAppInForeground
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        Log.d(TAG, "Message notification payload: ${remoteMessage.notification}")

        // Variables para construir el objeto Notification local
        var mensaje: String? = null
        var titulo: String? = null
        var tipo: String? = "info"
        var fecha: String = "Ahora"
        var estacionNombre: String? = null
        var prioridad: String? = "baja"

        // Procesar el payload de datos si está presente
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Processing data payload: ${remoteMessage.data}")
            tipo = remoteMessage.data["tipo_msg"] ?: remoteMessage.data["tipo"] ?: tipo
            estacionNombre = remoteMessage.data["estacion_nombre"]
            prioridad = remoteMessage.data["prioridad"] ?: prioridad

            // Intentar obtener la fecha formateada si viene en el payload
            fecha = remoteMessage.data["fecha"] ?: fecha
        }

        // Obtener título y mensaje del payload de notificación
        if (remoteMessage.notification != null) {
            titulo = remoteMessage.notification!!.title
            mensaje = remoteMessage.notification!!.body
        }

        // Si tenemos un mensaje, procedemos
        if (mensaje != null) {
            // Añadir el nombre de la estación al tipo si está disponible
            val tipoConEstacion = if (estacionNombre != null) {
                "$tipo - $estacionNombre"
            } else {
                tipo
            }

            val notification = Notification(tipoConEstacion, mensaje, fecha)

            // Guardar la notificación en el repositorio local
            NotificationRepository.addNotification(notification)
            Log.d(TAG, "Notification added to repository: $notification")

            // Mostrar la notificación del sistema con prioridad ajustada
            showSystemNotification(notification, prioridad ?: "baja", titulo)

            // Si la app está en primer plano, enviar broadcast
            if (isAppInForeground()) {
                Log.d(TAG, "App is in foreground. Sending broadcast: NEW_NOTIFICATION")
                sendBroadcast(Intent("NEW_NOTIFICATION"))
            }
        } else {
            Log.w(TAG, "No message content found in FCM message")
        }
    }

    /**
     * Se llama cuando el token de registro de FCM se actualiza.
     * <p>
     * Esto puede ocurrir si el token anterior ha expirado, si la app se restaura en un
     * nuevo dispositivo, o si el usuario desinstala/reinstala la app.
     * Es crucial enviar este nuevo token a tu servidor para asegurar que las notificaciones
     * sigan llegando al dispositivo correcto.
     * </p>
     *
     * @param token El nuevo token FCM.
     * @see sendRegistrationToServer
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token (onNewToken): $token")

        // Guarda el nuevo token en SharedPreferences para uso local si es necesario.
        // Considera usar Constantes.PREFS_NAME si es el archivo de preferencias principal.
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()

        // Envía el nuevo token a tu servidor backend.
        sendRegistrationToServer(token)
    }

    /**
     * Envía el token de registro FCM al servidor de la aplicación.
     * <b>Este método debe ser implementado</b> para comunicarse con tu API backend.
     *
     * @param token El token FCM a enviar.
     */
    private fun sendRegistrationToServer(token: String) {
        // TODO: Implementar la lógica para enviar el token a tu servidor.
        // Esto usualmente implica una llamada de red a un endpoint de tu API.
        Log.d(TAG, "Attempting to send token to server: $token (Placeholder - implement actual call)")
        // Ejemplo: YourApiClient.updateFcmToken(userId, token)
    }

    /**
     * Comprueba si la aplicación está actualmente en primer plano.
     *
     * @return `true` si la aplicación está en primer plano, `false` en caso contrario.
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // Obtiene la lista de procesos en ejecución.
        val processes = activityManager.runningAppProcesses ?: return false // Si no hay procesos, asume que no está en primer plano.
        val packageName = packageName // El nombre del paquete de esta aplicación.

        // Itera sobre los procesos para encontrar el de esta aplicación y verificar su estado.
        return processes.any { process ->
            process.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND && // El proceso es visible y activo.
                    process.processName == packageName // El nombre del proceso coincide con el de la app.
        }
    }

    /**
     * Construye y muestra una notificación del sistema.
     * <p>
     * Configura un [PendingIntent] para abrir [InicioActivity] y navegar al
     * fragmento de notificaciones cuando el usuario toque la notificación.
     * Crea un canal de notificación si se ejecuta en Android Oreo (API 26) o superior.
     * </p>
     *
     * @param notification El objeto [Notification] local que contiene los datos a mostrar.
     * @see InicioActivity
     * @see R.id.nav_notifications // Asumido para la navegación al fragmento de notificaciones
     */
    private fun showSystemNotification(notification: Notification, prioridad: String, titulo: String?) {
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("openFragment", "notificaciones")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Seleccionar el canal según la prioridad
        val channelId = when(prioridad) {
            "alta" -> "high_importance"
            "media" -> "medium_importance"
            else -> "default"
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Ajustar la prioridad de la notificación
        val notificationPriority = when(prioridad) {
            "alta" -> NotificationCompat.PRIORITY_HIGH
            "media" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_tren_round)
            .setColor(resources.getColor(R.color.orange, theme))
            .setContentTitle(titulo ?: getTitleByType(notification.tipo))
            .setContentText(notification.mensaje ?: "Tienes una nueva notificación")
            .setPriority(notificationPriority)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(if (prioridad == "alta") longArrayOf(0, 500, 250, 500) else longArrayOf(0, 250, 250, 250))
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canales de notificación para Android Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal de alta prioridad
            val highChannel = NotificationChannel(
                "high_importance",
                "Notificaciones Urgentes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones urgentes y de alta prioridad"
                enableLights(true)
                enableVibration(true)
            }

            // Canal de prioridad media
            val mediumChannel = NotificationChannel(
                "medium_importance",
                "Notificaciones Importantes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de importancia media"
                enableVibration(true)
            }

            // Canal por defecto
            val defaultChannel = NotificationChannel(
                "default",
                "Notificaciones Generales",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones generales de la aplicación"
            }

            notificationManager.createNotificationChannel(highChannel)
            notificationManager.createNotificationChannel(mediumChannel)
            notificationManager.createNotificationChannel(defaultChannel)
        }

        val notificationId = notification.mensaje?.hashCode() ?: Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "System notification shown with ID: $notificationId, priority: $prioridad")
    }

    /**
     * Devuelve un título formateado para la notificación basado en su tipo.
     *
     * @param tipo El tipo de notificación (ej. "alerta", "retraso", "info").
     * @return Un [String] con el título para la notificación.
     */
    private fun getTitleByType(tipo: String?): String {
        return when (tipo?.lowercase()) {
            "general" -> "📢 CONECTATEC"
            "horario" -> "🕐 CAMBIO DE HORARIO"
            "estacion" -> "🚉 ESTACIÓN"
            "promocion" -> "🎫 PROMOCIÓN"
            "mantenimiento" -> "🔧 MANTENIMIENTO"
            "urgente" -> "🚨 URGENTE"
            "alerta" -> "⚠️ ALERTA"
            "retraso" -> "⏱️ RETRASO"
            "info" -> "ℹ️ INFORMACIÓN"
            else -> "CONECTATEC"
        }
    }
}