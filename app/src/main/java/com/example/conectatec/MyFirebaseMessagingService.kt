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
        Log.d(TAG, "From: ${remoteMessage.from}") // Origen del mensaje
        Log.d(TAG, "Message data payload: ${remoteMessage.data}") // Payload de datos
        Log.d(TAG, "Message notification payload: ${remoteMessage.notification}") // Payload de notificación

        // Variables para construir nuestro objeto Notification local
        var mensaje: String? = null
        var tipo: String? = "info" // Tipo por defecto si no se especifica
        var fecha: String = "Ahora" // Fecha por defecto si no se especifica

        // Prioriza el payload de datos si está presente.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Processing data payload: ${remoteMessage.data}")
            tipo = remoteMessage.data["tipo"] ?: tipo // Usa el tipo de los datos, o el por defecto
            mensaje = remoteMessage.data["mensaje"] // El mensaje principal
            fecha = remoteMessage.data["fecha"] ?: fecha // La fecha de los datos, o la por defecto
        }

        // Si no se encontró un mensaje en el payload de datos,
        // intenta usar el cuerpo del payload de notificación como respaldo.
        if (mensaje == null && remoteMessage.notification != null) {
            mensaje = remoteMessage.notification!!.body
            // Podrías también extraer el título del remoteMessage.notification!!.title si es necesario
        }

        // Si después de procesar ambos payloads tenemos un mensaje, procedemos.
        if (mensaje != null) {
            val notification = Notification(tipo, mensaje, fecha) // Crea el objeto Notification local

            // Guarda la notificación en el repositorio local para su visualización en la app.
            NotificationRepository.addNotification(notification)
            Log.d(TAG, "Notification added to repository: $notification")

            // Muestra siempre una notificación del sistema al usuario.
            showSystemNotification(notification)

            // Si la aplicación está actualmente en primer plano,
            // envía un broadcast para que la UI pueda reaccionar (ej. actualizar una lista de notificaciones).
            if (isAppInForeground()) {
                Log.d(TAG, "App is in foreground. Sending broadcast: NEW_NOTIFICATION")
                sendBroadcast(Intent("NEW_NOTIFICATION")) // La actividad/fragmento debe registrar un BroadcastReceiver para esta acción.
            }
        } else {
            Log.w(TAG, "No message content found in FCM message to display.")
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
    private fun showSystemNotification(notification: Notification) {
        // Intent para abrir InicioActivity y pasarle data para que abra el fragmento de notificaciones.
        val intent = Intent(this, InicioActivity::class.java).apply {
            putExtra("openFragment", "notificaciones") // Extra para indicar a InicioActivity qué hacer.
            // Flags para manejar la pila de actividades: si InicioActivity ya está abierta, la trae al frente.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // PendingIntent que se ejecutará cuando el usuario toque la notificación.
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, // requestCode, puede ser único si necesitas manejar múltiples PendingIntents de forma diferente.
            intent,
            // FLAG_IMMUTABLE es requerido para apps que apuntan a Android S (API 31) o superior.
            // FLAG_UPDATE_CURRENT asegura que los extras del intent se actualicen si el PendingIntent ya existía.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "notificaciones_channel" // ID del canal de notificación, debe coincidir con el usado en AndroidManifest.xml.
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) // Sonido por defecto.

        // Construye la notificación del sistema.
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_tren_round) // Icono pequeño (obligatorio).
            .setColor(resources.getColor(R.color.orange, theme)) // Color del icono y acentos (API 21+). Asegúrate que R.color.orange existe.
            .setContentTitle(getTitleByType(notification.tipo)) // Título de la notificación.
            .setContentText(notification.mensaje ?: "Tienes una nueva notificación") // Contenido del mensaje.
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Prioridad para notificaciones importantes.
            .setAutoCancel(true) // La notificación se cierra al tocarla.
            .setSound(defaultSoundUri) // Sonido de la notificación.
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Patrón de vibración: sin retraso, vibra 250ms, pausa 250ms, vibra 250ms.
            .setContentIntent(pendingIntent) // Acción al tocar la notificación.

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Para Android Oreo (API 26) y superior, es obligatorio crear un canal de notificación.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones Generales", // Nombre del canal visible para el usuario.
                NotificationManager.IMPORTANCE_HIGH // Importancia del canal.
            ).apply {
                description = "Canal para todas las notificaciones de la aplicación ConectaTec." // Descripción del canal.
                enableLights(true) // Habilita luces LED si el dispositivo las soporta.
                // Luz LED color, puedes definirla si quieres un color específico
                // lightColor = Color.RED
                enableVibration(true) // Habilita vibración para este canal.
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Genera un ID único para la notificación. Usar el hashCode del mensaje
        // puede ayudar a agrupar o reemplazar notificaciones similares si es necesario,
        // o Random.nextInt() para asegurar que cada una sea nueva.
        // Considera una estrategia de ID más robusta si necesitas actualizar/cancelar notificaciones específicas.
        val notificationId = notification.mensaje?.hashCode() ?: Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build()) // Muestra la notificación.

        Log.d(TAG, "System notification shown with ID: $notificationId and content: ${notification.mensaje}")
    }

    /**
     * Devuelve un título formateado para la notificación basado en su tipo.
     *
     * @param tipo El tipo de notificación (ej. "alerta", "retraso", "info").
     * @return Un [String] con el título para la notificación.
     */
    private fun getTitleByType(tipo: String?): String {
        return when (tipo?.lowercase()) { // Compara en minúsculas para ser más robusto.
            "alerta" -> "⚠️ ALERTA"
            "retraso" -> "⏱️ RETRASO"
            "info" -> "ℹ️ INFORMACIÓN" // Añadido emoji para consistencia
            "estacion" -> "🚉 ESTACIÓN" // Añadido emoji
            "afluencia" -> "📊 AFLUENCIA" // Añadido emoji
            "horario" -> "🗓️ HORARIO" // Añadido emoji
            else -> "CONECTATEC" // Título por defecto.
        }
    }
}