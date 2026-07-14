package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.ui.components.TransactionRow
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onNavigateToBudgets: () -> Unit,
    onViewAllTransactions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).budgetRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Home", style = MaterialTheme.typography.headlineSmall)
        SummaryCardsSection(uiState)
        BudgetOverviewSection(uiState.budgetOverview, onNavigateToBudgets = onNavigateToBudgets)
        TopCategoriesSection(uiState.topCategories)
        RecentTransactionsSection(uiState.recentTransactions, onViewAll = onViewAllTransactions)
    }
}

@Composable
private fun SummaryCardsSection(uiState: HomeUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                title = "Income",
                amountCents = uiState.monthlyIncomeCents,
                amountColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Expenses",
                amountCents = uiState.monthlyExpenseCents,
                amountColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
        BalanceCard(balanceCents = uiState.monthlyBalanceCents)
    }
}

@Composable
private fun SummaryCard(title: String, amountCents: Long, amountColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAmountInCents(amountCents), style = MaterialTheme.typography.titleLarge, color = amountColor)
        }
    }
}

@Composable
private fun BalanceCard(balanceCents: Long, modifier: Modifier = Modifier) {
    val color = when {
        balanceCents > 0L -> MaterialTheme.colorScheme.primary
        balanceCents < 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (balanceCents < 0L) "-" else ""
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "This month's calculated balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("$sign${formatAmountInCents(abs(balanceCents))}", style = MaterialTheme.typography.headlineMedium, color = color)
        }
    }
}

@Composable
private fun BudgetOverviewSection(
    overview: BudgetOverview,
    onNavigateToBudgets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Budget overview", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            if (!overview.hasBudgets) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No monthly budgets yet")
                    Text(
                        "Create budgets to track your category spending.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onNavigateToBudgets) { Text("Go to Budgets") }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val budgetLabel = if (overview.activeBudgetCount == 1) "budget" else "budgets"
                    Text("${overview.activeBudgetCount} active $budgetLabel")
                    Text("Spent ${formatAmountInCents(overview.totalSpentCents)} of ${formatAmountInCents(overview.totalLimitCents)}")

                    LinearProgressIndicator(
                        progress = { overview.clampedUtilizationFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (overview.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

                    val percentText = "${(overview.utilizationFraction * 100).roundToInt()}% used" +
                        if (overview.isOverBudget) " — over budget" else ""
                    Text(
                        percentText,
                        color = if (overview.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(onClick = onNavigateToBudgets) { Text("View Budgets") }
                }
            }
        }
    }
}

@Composable
private fun TopCategoriesSection(categories: List<CategorySpending>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Top spending categories", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            if (categories.isEmpty()) {
                Text(
                    "No expenses recorded this month yet.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    categories.forEach { category -> TopCategoryRow(category) }
                }
            }
        }
    }
}

@Composable
private fun TopCategoryRow(category: CategorySpending, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatCategoryDisplayName(category.categoryName), style = MaterialTheme.typography.bodyLarge)
            Text(formatAmountInCents(category.totalCents), style = MaterialTheme.typography.bodyLarge)
        }
        LinearProgressIndicator(
            progress = { category.percentOfExpenses.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "${(category.percentOfExpenses * 100).roundToInt()}% of monthly expenses",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<TransactionEntity>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onViewAll) { Text("View all") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            if (transactions.isEmpty()) {
                Text(
                    "No transactions yet",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    transactions.forEachIndexed { index, transaction ->
                        TransactionRow(transaction = transaction)
                        if (index < transactions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}
