package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.factory((LocalContext.current.applicationContext as SpendWiseApplication).authRepository)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("SpendWise", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in to sync your data across devices", style = MaterialTheme.typography.bodyMedium)

        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                isError = uiState.emailError != null,
                supportingText = { uiState.emailError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                isError = uiState.passwordError != null,
                supportingText = { uiState.passwordError?.let { Text(it) } },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            uiState.loginError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::login,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Text("Log in")
                }
            }

            TextButton(onClick = onNavigateToForgotPassword, modifier = Modifier.fillMaxWidth()) {
                Text("Forgot password?")
            }
            TextButton(onClick = onNavigateToSignUp, modifier = Modifier.fillMaxWidth()) {
                Text("Don't have an account? Sign up")
            }
        }
    }
}
