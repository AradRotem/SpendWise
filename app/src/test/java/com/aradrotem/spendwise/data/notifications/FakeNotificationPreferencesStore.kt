package com.aradrotem.spendwise.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotificationPreferencesStore(
    budgetAlertsEnabled: Boolean = true,
    recurringRemindersEnabled: Boolean = true,
    sharedGroupNotificationsEnabled: Boolean = true
) : NotificationPreferencesStore {
    override val budgetAlertsEnabled = MutableStateFlow(budgetAlertsEnabled)
    override val recurringRemindersEnabled = MutableStateFlow(recurringRemindersEnabled)
    override val sharedGroupNotificationsEnabled = MutableStateFlow(sharedGroupNotificationsEnabled)
    override val permissionRequested = MutableStateFlow(false)

    override fun setBudgetAlertsEnabled(enabled: Boolean) { budgetAlertsEnabled.value = enabled }
    override fun setRecurringRemindersEnabled(enabled: Boolean) { recurringRemindersEnabled.value = enabled }
    override fun setSharedGroupNotificationsEnabled(enabled: Boolean) { sharedGroupNotificationsEnabled.value = enabled }
    override fun setPermissionRequested(requested: Boolean) { permissionRequested.value = requested }
}
