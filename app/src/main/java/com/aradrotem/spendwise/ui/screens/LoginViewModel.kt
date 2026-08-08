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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, loginError = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null, loginError = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return

        val emailError = if (!isValidEmail(state.email)) "Enter a valid email address." else null
        val passwordError = if (state.password.isBlank()) "Password is required." else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, loginError = null) }
        viewModelScope.launch {
            authRepository.login(state.email.trim(), state.password)
                .onSuccess { _uiState.update { it.copy(isLoading = false, isLoggedIn = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, loginError = error.message ?: "Login failed. Please try again.") }
                }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository) = viewModelFactory {
            initializer { LoginViewModel(authRepository) }
        }
    }
}
