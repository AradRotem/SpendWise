package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.auth.UserProfile
import com.aradrotem.spendwise.data.auth.UserProfileRepository
import com.aradrotem.spendwise.data.sync.SyncEngine
import com.aradrotem.spendwise.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val email: String = "",
    val displayName: String = "",
    val preferredCurrency: String = "USD",
    val monthlyBudgetText: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isLoggedOut: Boolean = false
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val syncEngine: SyncEngine?,
    private val onSyncNow: () -> Unit
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    // Null syncEngine (legacy/no-account database, or Firebase not configured for this build)
    // shows as a fixed "Offline" status - see FirebaseAvailability.
    val syncStatus: StateFlow<SyncStatus> = syncEngine?.status ?: MutableStateFlow(SyncStatus.Offline)
    val lastSyncedAt: StateFlow<Long?> = syncEngine?.lastSyncedAt
        ?: MutableStateFlow(null)

    fun syncNow() {
        onSyncNow()
    }

    private val uid: String? = authRepository.currentUid

    init {
        val currentUid = uid
        if (currentUid == null) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val profile = userProfileRepository.getProfile(currentUid)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        email = profile?.email ?: "",
                        displayName = profile?.displayName ?: "",
                        preferredCurrency = profile?.preferredCurrency ?: "USD",
                        monthlyBudgetText = profile?.monthlyBudget?.toString() ?: ""
                    )
                }
            }
        }
    }

    fun onDisplayNameChange(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, saveError = null) }
    }

    fun onPreferredCurrencyChange(currency: String) {
        _uiState.update { it.copy(preferredCurrency = currency, saveError = null) }
    }

    fun onMonthlyBudgetChange(text: String) {
        _uiState.update { it.copy(monthlyBudgetText = text, saveError = null) }
    }

    fun save() {
        val currentUid = uid ?: return
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val profile = UserProfile(
                uid = currentUid,
                displayName = state.displayName.trim(),
                email = state.email,
                preferredCurrency = state.preferredCurrency,
                monthlyBudget = state.monthlyBudgetText.toLongOrNull() ?: 0L,
                updatedAt = System.currentTimeMillis()
            )
            userProfileRepository.upsertProfile(profile)
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            userProfileRepository: UserProfileRepository,
            syncEngine: SyncEngine?,
            onSyncNow: () -> Unit
        ) = viewModelFactory {
            initializer { AccountViewModel(authRepository, userProfileRepository, syncEngine, onSyncNow) }
        }
    }
}
