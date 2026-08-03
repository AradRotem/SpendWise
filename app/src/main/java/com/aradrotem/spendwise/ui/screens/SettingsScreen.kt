package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    onNavigateToRecurringPayments: () -> Unit,
    onNavigateToMonthlyReport: () -> Unit,
    onNavigateToGroupExpenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(text = "Settings", modifier = Modifier.padding(16.dp))
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
            headlineContent = { Text("Monthly report") },
            supportingContent = { Text("View and share income, expenses, and budgets for any month") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToMonthlyReport)
        )
        ListItem(
            headlineContent = { Text("Group expenses") },
            supportingContent = { Text("Split shared expenses with friends and track who owes whom") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToGroupExpenses)
        )
    }
}
