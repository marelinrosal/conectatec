package com.example.conectatec

/**
 * Objeto singleton que actúa como un repositorio en memoria para las notificaciones.
 * <p>
 * Proporciona una lista de notificaciones ([notifications]) y métodos para
 * añadir nuevas notificaciones ([addNotification]) o reemplazar todas las
 * notificaciones existentes ([addAllNotifications]).
 * Las notificaciones se almacenan en una lista mutable interna, pero se exponen
 * como una lista inmutable para proteger el acceso directo desde fuera del repositorio.
 * </p>
 * <p>
 * Al añadir una nueva notificación con [addNotification], esta se inserta al principio
 * de la lista, lo que significa que las notificaciones más recientes aparecerán primero
 * si se itera la lista en su orden natural.
 * </p>
 *
 * @property _notifications Lista mutable interna que almacena las instancias de [Notification].
 *                         Es privada para controlar el acceso.
 * @property notifications Lista inmutable ([List]) de [Notification] expuesta públicamente.
 *                       Proporciona acceso de solo lectura a las notificaciones almacenadas.
 *
 * @author Ortiz Gallegos Starenka Susana
 * @author Salgado Rojas Marelin Iral
 * @author Orozco Reyes Hiram
 * @author Ortiz Ceballos Jorge
 * @version 1.15
 * @since 22 Marzo 2025
 * @see Notification
 */
object NotificationRepository {

    /**
     * Lista mutable interna que almacena las notificaciones.
     * Esta lista es privada para encapsular la lógica de modificación.
     */
    private val _notifications = mutableListOf<Notification>()

    /**
     * Proporciona acceso de solo lectura a la lista de notificaciones.
     * Devuelve una copia inmutable de la lista interna de notificaciones.
     * Las notificaciones están ordenadas con la más reciente primero (si se usa [addNotification]).
     */
    val notifications: List<Notification> get() = _notifications.toList() // Devuelve una copia para mayor inmutabilidad

    /**
     * Añade una nueva notificación al principio de la lista.
     * <p>
     * Esto asegura que las notificaciones más recientes aparezcan primero
     * al acceder a la lista [notifications].
     * </p>
     *
     * @param notification La [Notification] a añadir.
     */
    fun addNotification(notification: Notification) {
        _notifications.add(0, notification) // Añade al inicio de la lista.
    }

    /**
     * Reemplaza todas las notificaciones existentes con una nueva lista de notificaciones.
     * <p>
     * Primero, se limpia la lista interna de notificaciones y luego se añaden
     * todas las notificaciones de la lista proporcionada.
     * </p>
     *
     * @param newNotifications La lista de [Notification] que reemplazará las existentes.
     */
    fun addAllNotifications(newNotifications: List<Notification>) {
        _notifications.clear() // Limpia las notificaciones antiguas.
        _notifications.addAll(newNotifications) // Añade todas las nuevas notificaciones.
    }

    // Podrías considerar añadir otros métodos útiles aquí, como:
    // fun clearNotifications() { _notifications.clear() }
    // fun getNotificationById(id: String): Notification? { /* ... */ }
    // fun removeNotification(notification: Notification) { _notifications.remove(notification) }
}