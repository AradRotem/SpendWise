package com.aradrotem.spendwise.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aradrotem.spendwise.R

// Thin wrapper around the actual Android notification APIs, kept behind an interface (same
// pattern as ReceiptStorageRepository/SyncWatermarkStore) so the pure event-detection logic
// (BudgetAlertEvaluator, RecurringReminderEvaluator, NotificationCheckPlanner) never needs a real
// NotificationManager to be unit-tested, and a fake implementation is trivial for any test that
// does want to assert "a notification was sent."
interface NotificationSender {
    fun send(channelId: String, notificationId: Int, title: String, text: String)
}

class SystemNotificationSender(private val context: Context) : NotificationSender {
    override fun send(channelId: String, notificationId: Int, title: String, text: String) {
        // POST_NOTIFICATIONS can be denied (or not yet granted) at any time - silently skipping
        // is correct here, never crash or throw; the rest of the app must keep working regardless.
        val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
