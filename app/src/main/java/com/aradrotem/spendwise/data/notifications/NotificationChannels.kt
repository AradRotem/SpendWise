package com.aradrotem.spendwise.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

// Three channels, matching Step 19's suggested split exactly - each lets the user separately mute
// one category from system settings even if they leave all three of SpendWise's own in-app
// toggles on.
object NotificationChannels {
    const val BUDGET_ALERTS = "budget_alerts"
    const val RECURRING_PAYMENTS = "recurring_payments"
    const val SHARED_GROUPS = "shared_groups"

    // Safe to call on every app start - createNotificationChannel is a no-op if the channel
    // already exists with the same id. No-op below API 26, where channels don't exist.
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(BUDGET_ALERTS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when a category budget is approaching or over its limit"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(RECURRING_PAYMENTS, "Recurring payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for upcoming recurring payments and installments"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(SHARED_GROUPS, "Shared groups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Invitations and activity in shared expense groups"
            }
        )
    }
}
