package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.sync.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    app: SpendWiseApplication = LocalContext.current.applicationContext as SpendWiseApplication,
    viewModel: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(
            app.authRepository,
            app.userProfileRepository,
            app.repositories.syncEngine,
            onSyncNow = { app.triggerSync() }
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(uiState.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.preferredCurrency,
                onValueChange = viewModel::onPreferredCurrencyChange,
                label = { Text("Preferred currency") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.monthlyBudgetText,
                onValueChange = viewModel::onMonthlyBudgetChange,
                label = { Text("Monthly budget") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = viewModel::save, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.isSaving) "Saving…" else "Save")
            }

            HorizontalDivider()

            Text("Cloud sync", style = MaterialTheme.typography.titleMedium)
            Text(syncStatusText(syncStatus, lastSyncedAt), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) {
                Text("Sync now")
            }

            HorizontalDivider()

            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Log out")
            }
        }
    }
}

private fun syncStatusText(status: SyncStatus, lastSyncedAt: Long?): String {
    val statusLabel = when (status) {
        is SyncStatus.Offline -> "Offline — changes will sync later"
        is SyncStatus.Syncing -> "Syncing…"
        is SyncStatus.Synced -> "Synced"
        is SyncStatus.Error -> "Sync error: ${status.message}"
    }
    if (status !is SyncStatus.Synced || lastSyncedAt == null) return statusLabel
    val secondsAgo = (System.currentTimeMillis() - lastSyncedAt) / 1000
    return "$statusLabel · last synced ${secondsAgo}s ago"
}
