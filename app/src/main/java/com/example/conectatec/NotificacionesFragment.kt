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
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.github.jan.supabase.SupabaseClient


class NotificacionesFragment : Fragment() {
    private lateinit var notificationsContainer: LinearLayout

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
        loadNotifications()
    }

    private fun loadNotifications() {
        // 1. Limpiar contenedor
        notificationsContainer.removeAllViews()

        // 2. Ejemplo con datos estáticos (reemplazar con tu lógica de Supabase)
        val sampleNotifications = listOf(
            Notification("alerta", "⚠️ ALERTA: Tramo suspendido", "Hace 5 minutos"),
            Notification("retraso", "⏱️ RETRASO: 15 minutos", "Hace 18 minutos")
        )

        // 3. Añadir notificaciones al contenedor
        sampleNotifications.forEach { notification ->
            val notificationView = createNotificationView(notification)
            notificationsContainer.addView(notificationView)
        }
    }

    private fun createNotificationView(notification: Notification): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.notification_item, notificationsContainer, false)

        val iconView = view.findViewById<ImageView>(R.id.iv_icon) // Asegúrate de tener este ID en tu XML
        val titleView = view.findViewById<TextView>(R.id.tv_title)
        val timeView = view.findViewById<TextView>(R.id.tv_time)

        titleView.text = notification.mensaje
        timeView.text = notification.fecha

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

data class NotificationStyle(
    val iconRes: Int,
    val titlePrefix: String,
    @ColorInt val titleColor: Int,
    @ColorInt val backgroundColor: Int
)

