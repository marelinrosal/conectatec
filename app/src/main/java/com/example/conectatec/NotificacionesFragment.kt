package com.example.conectatec

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
// import androidx.annotation.ColorInt // No se usa directamente
import androidx.fragment.app.Fragment
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Fragmento que muestra una lista de notificaciones recibidas por la aplicación.
 * <p>
 * Este fragmento carga las notificaciones desde [NotificationRepository] y las
 * muestra en un [LinearLayout]. También registra un [BroadcastReceiver] para
 * escuchar la acción "NEW_NOTIFICATION", que se envía cuando llega una nueva
 * notificación mientras la app está en primer plano, permitiendo actualizar la
 * lista dinámicamente.
 * </p>
 * <p>
 * Si no hay notificaciones, muestra un mensaje indicándolo y, como ejemplo,
 * puede agregar algunas notificaciones de muestra al repositorio.
 * </p>
 *
 * @property notificationsContainer El [LinearLayout] donde se añaden las vistas de cada notificación.
 * @property notificationReceiver El [BroadcastReceiver] para escuchar nuevas notificaciones.
 * @property TAG Etiqueta para logging, con valor "NotificacionesFragment".
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see NotificationRepository
 * @see MyFirebaseMessagingService
 * @see R.layout.fragment_notificaciones
 * @see R.layout.notification_item
 */
class NotificacionesFragment : Fragment() {
    private lateinit var notificationsContainer: LinearLayout
    private var notificationReceiver: BroadcastReceiver? = null
    private val TAG = "NotificacionesFragment" // Etiqueta para logs

    /**
     * Se llama para que el fragmento instancie su vista de interfaz de usuario.
     * Infla el layout definido en `R.layout.fragment_notificaciones`.
     *
     * @param inflater El [LayoutInflater] que se puede usar para inflar vistas.
     * @param container El [ViewGroup] padre al que se adjuntará la UI del fragmento.
     * @param savedInstanceState Estado previamente guardado, si existe.
     * @return La [View] raíz para la UI del fragmento.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla el layout para este fragmento
        Log.d(TAG, "onCreateView llamado.")
        return inflater.inflate(R.layout.fragment_notificaciones, container, false)
    }

    /**
     * Se llama inmediatamente después de que [onCreateView] ha retornado.
     * <p>
     * Inicializa `notificationsContainer`, carga las notificaciones existentes
     * y registra el [BroadcastReceiver] para escuchar actualizaciones de notificaciones
     * (cuando llegan mientras la app está en primer plano).
     * </p>
     *
     * @param view La [View] devuelta por [onCreateView].
     * @param savedInstanceState Estado previamente guardado, si existe.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated llamado.")
        notificationsContainer = view.findViewById(R.id.notifications_container)

        // Comprueba si el fragmento fue abierto a través de una notificación del sistema.
        // Útil para depuración o lógica específica si es necesario.
        activity?.intent?.let { intent ->
            if (intent.getStringExtra("openFragment") == "notificaciones") {
                Log.d(TAG, "Fragmento abierto directamente desde una notificación del sistema.")
                // Considerar limpiar el extra para evitar reprocesamiento si el usuario navega
                // fuera y vuelve al fragmento sin cerrar la actividad:
                // activity?.intent?.removeExtra("openFragment")
            }
        }

        loadNotifications() // Carga y muestra las notificaciones iniciales.

        // Configura y registra el BroadcastReceiver para la acción "NEW_NOTIFICATION".
        // Esto permite actualizar la lista de notificaciones en tiempo real si llega una nueva
        // mientras este fragmento está visible.
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "NEW_NOTIFICATION") {
                    Log.d(TAG, "Broadcast 'NEW_NOTIFICATION' recibido. Recargando notificaciones.")
                    loadNotifications() // Recarga las notificaciones para mostrar la nueva.
                }
            }
        }

        // Registra el receptor. Es importante usar ContextCompat para el registro seguro.
        // RECEIVER_NOT_EXPORTED asegura que el receptor solo reciba broadcasts de esta app.
        context?.let {
            ContextCompat.registerReceiver(
                it,
                notificationReceiver,
                IntentFilter("NEW_NOTIFICATION"), // Escucha la acción específica.
                ContextCompat.RECEIVER_NOT_EXPORTED // Seguridad: el receptor no es exportado.
            )
        }
        Log.d(TAG, "BroadcastReceiver para NEW_NOTIFICATION registrado.")
    }

    /**
     * Se llama cuando la vista asociada con el fragmento está siendo destruida.
     * Desregistra el [notificationReceiver] para evitar fugas de memoria y
     * comportamiento inesperado.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView llamado.")
        // Desregistra el BroadcastReceiver para prevenir memory leaks.
        notificationReceiver?.let {
            context?.unregisterReceiver(it)
            Log.d(TAG, "BroadcastReceiver para NEW_NOTIFICATION desregistrado.")
            notificationReceiver = null // Limpia la referencia.
        }
    }

    /**
     * Carga las notificaciones desde [NotificationRepository] y las muestra en la UI.
     * <p>
     * Primero, limpia cualquier vista de notificación existente en `notificationsContainer`.
     * Luego, obtiene la lista de notificaciones. Si está vacía, muestra un mensaje
     * indicándolo y, como ejemplo, añade algunas notificaciones de muestra al repositorio
     * (este comportamiento de añadir muestras podría ser solo para desarrollo).
     * Finalmente, itera sobre las notificaciones (actualizadas si se añadieron muestras)
     * y crea una vista para cada una usando [createNotificationView], añadiéndola al contenedor.
     * </p>
     * @see NotificationRepository.notifications
     * @see NotificationRepository.addAllNotifications
     * @see createNotificationView
     */
    private fun loadNotifications() {
        Log.d(TAG, "Cargando notificaciones...")
        // 1. Limpiar el contenedor de vistas de notificaciones previas.
        notificationsContainer.removeAllViews()

        // 2. Obtener la lista actual de notificaciones del repositorio.
        var currentNotifications = NotificationRepository.notifications

        if (currentNotifications.isEmpty()) {
            Log.d(TAG, "No hay notificaciones. Mostrando mensaje y añadiendo ejemplos.")
            // Muestra un mensaje indicando que no hay notificaciones.
            val noNotificationsTextView = TextView(context).apply {
                text = "No tienes notificaciones en este momento."
                textSize = 16f // Considerar usar sp para tamaños de texto.
                // Para padding en dp, usar resources.getDimensionPixelSize(R.dimen.padding_value)
                setPadding(16, 32, 16, 16)
                gravity = android.view.Gravity.CENTER
            }
            notificationsContainer.addView(noNotificationsTextView)

            // (Opcional) Añadir notificaciones de ejemplo si el repositorio está vacío.
            // Esto es útil para desarrollo y demostración.
            // En producción, probablemente no querrías añadir datos de muestra aquí.
            /*Log.d(TAG, "Añadiendo notificaciones de ejemplo al repositorio.")
            val sampleNotifications = listOf(
                Notification("alerta", "⚠️ ALERTA: Servicio en tramo Observatorio-Santa Fe suspendido temporalmente.", "Hace 5 minutos"),
                Notification("retraso", "⏱️ RETRASO: Demora estimada de 15 minutos en la línea Zinacantepec-Lerma.", "Hace 18 minutos"),
                Notification("info", "ℹ️ INFO: Nuevo horario de fin de semana a partir del próximo Sábado.", "Hace 1 hora")
            )
            NotificationRepository.addAllNotifications(sampleNotifications) // Añade las muestras al repositorio.
            notificationsContainer.removeAllViews() // Limpia de nuevo para quitar el mensaje "No hay notificaciones".
            currentNotifications = NotificationRepository.notifications // Recarga las notificaciones que ahora incluyen las muestras.

             */
        }

        // 3. Añadir cada notificación al contenedor.
        // Se muestran en el orden en que están en el repositorio.
        // Si NotificationRepository las añade al principio de su lista interna, las más nuevas aparecerán primero.
        // Si se añaden al final, y quieres las más nuevas arriba, considera: currentNotifications.reversed().forEach { ... }
        Log.d(TAG, "Mostrando ${currentNotifications.size} notificaciones.")
        currentNotifications.forEach { notification ->
            val notificationView = createNotificationView(notification)
            notificationsContainer.addView(notificationView)
        }
    }

    /**
     * Crea y configura una vista para una notificación individual.
     * <p>
     * Infla el layout `R.layout.notification_item` y establece el icono,
     * el mensaje y la fecha de la notificación basándose en el objeto [Notification] proporcionado.
     * El icono se selecciona según el `tipo` de la notificación.
     * </p>
     *
     * @param notification El objeto [Notification] que contiene los datos a mostrar.
     * @return Una [View] configurada que representa la notificación.
     * @see R.layout.notification_item
     * @see R.drawable.ic_alert
     * @see R.drawable.ic_clock
     * @see R.drawable.ic_station
     * @see R.drawable.ic_people
     * @see R.drawable.ic_schedule
     * @see R.mipmap.ic_launcher_tren_round
     */
    private fun createNotificationView(notification: Notification): View {
        val inflater = LayoutInflater.from(requireContext())
        // Infla la vista del ítem de notificación. El `false` para attachToRoot es importante
        // ya que estamos añadiendo la vista manualmente al notificationsContainer.
        val view = inflater.inflate(R.layout.notification_item, notificationsContainer, false)

        // Obtiene las referencias a los elementos de la UI dentro del ítem.
        val iconView = view.findViewById<ImageView>(R.id.iv_icon)
        val titleView = view.findViewById<TextView>(R.id.tv_title) // Asumo que tv_title es para el mensaje.
        val timeView = view.findViewById<TextView>(R.id.tv_time)

        // Establece el contenido de la notificación.
        titleView.text = notification.mensaje ?: "Notificación sin mensaje"
        timeView.text = notification.fecha ?: "Fecha desconocida"

        // Asigna un icono basado en el tipo de notificación.
        val iconRes = when (notification.tipo?.lowercase()) { // Comparación en minúsculas para robustez.
            "alerta" -> R.drawable.ic_alert     // Asume que estos drawables existen en tu proyecto.
            "retraso" -> R.drawable.ic_clock
            "info" -> R.drawable.ic_station     // Agrupado info con el mismo icono que estacion.
            "estacion" -> R.drawable.ic_station
            "afluencia" -> R.drawable.ic_people
            "horario" -> R.drawable.ic_schedule
            else -> R.mipmap.ic_launcher_tren_round // Icono por defecto si el tipo no coincide o es nulo.
        }
        iconView.setImageResource(iconRes)
        Log.d(TAG, "Vista de notificación creada para: ${notification.mensaje}, tipo: ${notification.tipo}, icono: $iconRes")

        return view
    }
}