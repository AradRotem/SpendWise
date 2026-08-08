package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val requestError: String? = null,
    val isEmailSent: Boolean = false
)

class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, requestError = null) }
    }

    fun sendResetEmail() {
        val state = _uiState.value
        if (state.isLoading) return

        if (!isValidEmail(state.email)) {
            _uiState.update { it.copy(emailError = "Enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, requestError = null) }
        viewModelScope.launch {
            authRepository.sendPasswordReset(state.email.trim())
                .onSuccess { _uiState.update { it.copy(isLoading = false, isEmailSent = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, requestError = error.message ?: "Could not send reset email. Please try again.") }
                }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository) = viewModelFactory {
            initializer { ForgotPasswordViewModel(authRepository) }
        }
    }
}
