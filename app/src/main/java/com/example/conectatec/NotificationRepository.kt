package com.example.conectatec

object NotificationRepository {
    private val _notifications = mutableListOf<Notification>()
    val notifications: List<Notification> get() = _notifications

    fun addNotification(notification: Notification) {
        _notifications.add(0, notification)
    }

    fun addAllNotifications(notifications: List<Notification>) {

    }
}