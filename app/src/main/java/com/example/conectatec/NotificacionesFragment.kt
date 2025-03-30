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
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import android.util.Log
import androidx.core.content.ContextCompat

class NotificacionesFragment : Fragment() {
    private lateinit var notificationsContainer: LinearLayout
    private var notificationReceiver: BroadcastReceiver? = null

    override fun onCreateView(

        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notificaciones, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        notificationsContainer = view.findViewById(R.id.notifications_container)

        // Verificar si venimos de una notificación
        activity?.intent?.let { intent ->
            if (intent.getStringExtra("openFragment") == "notificaciones") {
                Log.d("NOTIFICATIONS", "Fragment abierto desde notificación")
            }
        }

        loadNotifications()

        // Registrar receptor para actualizar cuando llegue una notificación
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "NEW_NOTIFICATION") {
                    Log.d("NOTIFICATIONS", "Recibida actualización de notificaciones")
                    loadNotifications()
                }
            }
        }

        context?.let {
            ContextCompat.registerReceiver(
                it,
                notificationReceiver,
                IntentFilter("NEW_NOTIFICATION"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Desregistrar el receptor
        notificationReceiver?.let {
            context?.unregisterReceiver(it)
            notificationReceiver = null
        }
    }

    private fun loadNotifications() {
        // 1. Limpiar contenedor
        notificationsContainer.removeAllViews()

        // 2. Obtener notificaciones del repositorio
        val notifications = NotificationRepository.notifications

        if (notifications.isEmpty()) {
            // Mostrar mensaje de no notificaciones
            val textView = TextView(context).apply {
                text = "No tienes notificaciones"
                textSize = 16f
                setPadding(16, 32, 16, 16)
                gravity = android.view.Gravity.CENTER
            }
            notificationsContainer.addView(textView)

            // Agregar algunas notificaciones de ejemplo solo si no hay ninguna
            val sampleNotifications = listOf(
                Notification("alerta", "⚠️ ALERTA: Tramo suspendido", "Hace 5 minutos"),
                Notification("retraso", "⏱️ RETRASO: 15 minutos", "Hace 18 minutos")
            )

            NotificationRepository.addAllNotifications(sampleNotifications)
            notificationsContainer.removeAllViews()
        }

        // 3. Añadir notificaciones al contenedor
        NotificationRepository.notifications.forEach { notification ->
            val notificationView = createNotificationView(notification)
            notificationsContainer.addView(notificationView)
        }
    }

    private fun createNotificationView(notification: Notification): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.notification_item, notificationsContainer, false)

        val iconView = view.findViewById<ImageView>(R.id.iv_icon)
        val titleView = view.findViewById<TextView>(R.id.tv_title)
        val timeView = view.findViewById<TextView>(R.id.tv_time)

        titleView.text = notification.mensaje ?: "Notificación sin mensaje"
        timeView.text = notification.fecha ?: "Fecha desconocida"

        // Asignar icono según el tipo
        val iconRes = when (notification.tipo?.lowercase()) {
            "alerta" -> R.drawable.ic_alert
            "retraso" -> R.drawable.ic_clock
            "info", "estacion" -> R.drawable.ic_station
            "afluencia" -> R.drawable.ic_people
            "horario" -> R.drawable.ic_schedule
            else -> R.drawable.ic_notificacion // Icono por defecto
        }
        iconView.setImageResource(iconRes)

        return view
    }
}