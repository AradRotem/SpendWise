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

data class SignUpUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val signUpError: String? = null,
    val isSignedUp: Boolean = false
)

class SignUpViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, displayNameError = null, signUpError = null) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, signUpError = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null, confirmPasswordError = null, signUpError = null) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, confirmPasswordError = null, signUpError = null) }
    }

    fun signUp() {
        val state = _uiState.value
        if (state.isLoading) return

        val displayNameError = if (state.displayName.isBlank()) "Name is required." else null
        val emailError = if (!isValidEmail(state.email)) "Enter a valid email address." else null
        val passwordError = if (!isValidPassword(state.password)) "Password must be at least 6 characters." else null
        val confirmPasswordError = if (state.confirmPassword != state.password) "Passwords do not match." else null

        if (displayNameError != null || emailError != null || passwordError != null || confirmPasswordError != null) {
            _uiState.update {
                it.copy(
                    displayNameError = displayNameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, signUpError = null) }
        viewModelScope.launch {
            authRepository.signUp(state.displayName.trim(), state.email.trim(), state.password)
                .onSuccess { _uiState.update { it.copy(isLoading = false, isSignedUp = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, signUpError = error.message ?: "Sign up failed. Please try again.") }
                }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository) = viewModelFactory {
            initializer { SignUpViewModel(authRepository) }
        }
    }
}
