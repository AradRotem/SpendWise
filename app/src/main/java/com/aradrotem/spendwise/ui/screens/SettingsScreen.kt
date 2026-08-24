package com.aradrotem.spendwise.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication

@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    onNavigateToRecurringPayments: () -> Unit,
    onNavigateToReportsAnalytics: () -> Unit,
    onNavigateToGroupExpenses: () -> Unit,
    onNavigateToAccount: () -> Unit,
    modifier: Modifier = Modifier,
    notificationSettingsViewModel: NotificationSettingsViewModel = viewModel(
        factory = NotificationSettingsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.notificationPreferencesStore
        )
    )
) {
    // Settings is the only bottom-nav destination that keeps the global "Add transaction" FAB
    // visible (see NavigationVisibility.isFabVisible) while also being long enough to need
    // scrolling, so the bottom padding below is what keeps the last notification row from being
    // covered by that floating FAB - a plain fillMaxSize Column has no scroll of its own and would
    // otherwise just clip the lower notification toggles off-screen entirely.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Manage income and expense categories") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToCategories)
        )
        ListItem(
            headlineContent = { Text("Recurring Transactions") },
            supportingContent = { Text("Manage monthly expenses, salary, and installment purchases") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToRecurringPayments)
        )
        ListItem(
            headlineContent = { Text("Reports & analytics") },
            supportingContent = { Text("View reports, charts, trends, budgets, and spending insights") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToReportsAnalytics)
        )
        ListItem(
            headlineContent = { Text("Group expenses") },
            supportingContent = { Text("Split shared expenses with friends and track who owes whom") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToGroupExpenses)
        )
        ListItem(
            headlineContent = { Text("Account") },
            supportingContent = { Text("Profile, sync status, and sign out") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToAccount)
        )
        HorizontalDivider()
        NotificationSettingsSection(notificationSettingsViewModel)
    }
}

@Composable
private fun NotificationSettingsSection(viewModel: NotificationSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        viewModel.setPermissionRequested(true)
    }

    Text(
        text = "Notifications",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )

    // The app must keep working normally without this permission - toggles below still persist
    // the user's preference even while permission is denied, so nothing is lost once they grant it
    // later from system settings.
    if (!permissionGranted) {
        ListItem(
            headlineContent = { Text("Enable notifications") },
            supportingContent = { Text("Allow SpendWise to notify you about budgets and upcoming payments") },
            trailingContent = {
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Enable")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    ListItem(
        headlineContent = { Text("Budget alerts") },
        supportingContent = { Text("Notify when a category budget is nearly reached or exceeded") },
        trailingContent = {
            Switch(checked = uiState.budgetAlertsEnabled, onCheckedChange = viewModel::setBudgetAlertsEnabled)
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("Recurring payment reminders") },
        supportingContent = { Text("Notify the day before a recurring payment or installment is due") },
        trailingContent = {
            Switch(checked = uiState.recurringRemindersEnabled, onCheckedChange = viewModel::setRecurringRemindersEnabled)
        },
        modifier = Modifier.fillMaxWidth()
    )
    ListItem(
        headlineContent = { Text("Shared-group notifications") },
        supportingContent = { Text("Notify about invitations and new activity in shared expense groups") },
        trailingContent = {
            Switch(checked = uiState.sharedGroupNotificationsEnabled, onCheckedChange = viewModel::setSharedGroupNotificationsEnabled)
        },
        modifier = Modifier.fillMaxWidth()
    )
}
