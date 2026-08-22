package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.notifications.NotificationPreferencesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class NotificationSettingsUiState(
    val budgetAlertsEnabled: Boolean = true,
    val recurringRemindersEnabled: Boolean = true,
    val sharedGroupNotificationsEnabled: Boolean = true,
    val permissionRequested: Boolean = false
)

class NotificationSettingsViewModel(private val store: NotificationPreferencesStore) : ViewModel() {

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        store.budgetAlertsEnabled,
        store.recurringRemindersEnabled,
        store.sharedGroupNotificationsEnabled,
        store.permissionRequested
    ) { budgetAlerts, recurringReminders, sharedGroups, permissionRequested ->
        NotificationSettingsUiState(budgetAlerts, recurringReminders, sharedGroups, permissionRequested)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationSettingsUiState()
    )

    fun setBudgetAlertsEnabled(enabled: Boolean) = store.setBudgetAlertsEnabled(enabled)
    fun setRecurringRemindersEnabled(enabled: Boolean) = store.setRecurringRemindersEnabled(enabled)
    fun setSharedGroupNotificationsEnabled(enabled: Boolean) = store.setSharedGroupNotificationsEnabled(enabled)
    fun setPermissionRequested(requested: Boolean) = store.setPermissionRequested(requested)

    companion object {
        fun factory(store: NotificationPreferencesStore) = viewModelFactory {
            initializer { NotificationSettingsViewModel(store) }
        }
    }
}
