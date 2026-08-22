package com.aradrotem.spendwise.data.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Per-account notification preferences. All three categories default to enabled (opt-out, not
// opt-in) since a fresh install should behave usefully without the user having to find a settings
// screen first - matches how every other SpendWise feature works out of the box.
interface NotificationPreferencesStore {
    val budgetAlertsEnabled: StateFlow<Boolean>
    val recurringRemindersEnabled: StateFlow<Boolean>
    val sharedGroupNotificationsEnabled: StateFlow<Boolean>
    // Tracks whether the POST_NOTIFICATIONS runtime permission has already been requested once,
    // so the Settings screen only offers the system prompt again if the user explicitly taps
    // "Enable notifications" - never auto-launches it repeatedly on every screen visit.
    val permissionRequested: StateFlow<Boolean>

    fun setBudgetAlertsEnabled(enabled: Boolean)
    fun setRecurringRemindersEnabled(enabled: Boolean)
    fun setSharedGroupNotificationsEnabled(enabled: Boolean)
    fun setPermissionRequested(requested: Boolean)
}

// SharedPreferences-backed, following the same interface+impl split and per-account file naming
// as SharedPrefsSyncWatermarkStore (data/sync) - the closest existing precedent for wrapping a
// small piece of Android-framework persistence behind a testable interface.
class SharedPrefsNotificationPreferencesStore(context: Context, uid: String?) : NotificationPreferencesStore {
    private val prefs = context.getSharedPreferences("spendwise_notification_prefs_${uid ?: "legacy"}", Context.MODE_PRIVATE)

    override val budgetAlertsEnabled = MutableStateFlow(prefs.getBoolean(KEY_BUDGET_ALERTS, true))
    override val recurringRemindersEnabled = MutableStateFlow(prefs.getBoolean(KEY_RECURRING_REMINDERS, true))
    override val sharedGroupNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHARED_GROUPS, true))
    override val permissionRequested = MutableStateFlow(prefs.getBoolean(KEY_PERMISSION_REQUESTED, false))

    override fun setBudgetAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ALERTS, enabled).apply()
        budgetAlertsEnabled.value = enabled
    }

    override fun setRecurringRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECURRING_REMINDERS, enabled).apply()
        recurringRemindersEnabled.value = enabled
    }

    override fun setSharedGroupNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARED_GROUPS, enabled).apply()
        sharedGroupNotificationsEnabled.value = enabled
    }

    override fun setPermissionRequested(requested: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, requested).apply()
        permissionRequested.value = requested
    }

    private companion object {
        const val KEY_BUDGET_ALERTS = "budget_alerts_enabled"
        const val KEY_RECURRING_REMINDERS = "recurring_reminders_enabled"
        const val KEY_SHARED_GROUPS = "shared_group_notifications_enabled"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
