package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = viewModel(
        factory = ForgotPasswordViewModel.factory((LocalContext.current.applicationContext as SpendWiseApplication).authRepository)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reset password") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isEmailSent) {
                Text(
                    "If an account exists for that email, a password reset link has been sent.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Enter your account email and we'll send you a reset link.",
                    style = MaterialTheme.typography.bodyMedium
                )
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

                uiState.requestError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = viewModel::sendResetEmail,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    } else {
                        Text("Send reset email")
                    }
                }
            }
        }
    }
}
