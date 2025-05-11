package com.example.conectatec

/**
 * Representa una notificación dentro de la aplicación.
 * <p>
 * Esta clase de datos almacena la información esencial de una notificación,
 * como su tipo (que puede influir en cómo se muestra o qué icono usa),
 * el mensaje principal y la fecha o marca de tiempo de cuándo se recibió o generó.
 * Los campos son anulables para permitir flexibilidad en caso de que alguna
 * información no esté disponible.
 * </p>
 *
 * @property tipo El tipo de notificación (ej. "alerta", "info", "retraso").
 *                Este campo puede ser nulo si el tipo no está especificado.
 * @property mensaje El contenido textual principal de la notificación.
 *                   Puede ser nulo si el mensaje no está disponible.
 * @property fecha Una representación en cadena de la fecha o marca de tiempo de la notificación
 *                 (ej. "Hace 5 minutos", "2023-10-28 10:00").
 *                 Puede ser nulo si la fecha no está disponible.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 */
data class Notification(
    val tipo: String?,
    val mensaje: String?,
    val fecha: String?
)