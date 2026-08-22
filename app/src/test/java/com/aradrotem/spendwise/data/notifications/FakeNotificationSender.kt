package com.aradrotem.spendwise.data.notifications

data class SentNotification(val channelId: String, val notificationId: Int, val title: String, val text: String)

class FakeNotificationSender : NotificationSender {
    val sent = mutableListOf<SentNotification>()

    override fun send(channelId: String, notificationId: Int, title: String, text: String) {
        sent += SentNotification(channelId, notificationId, title, text)
    }
}
