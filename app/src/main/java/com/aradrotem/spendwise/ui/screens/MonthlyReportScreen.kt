package com.aradrotem.spendwise.ui.screens

import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatMonthYear
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthlyReportViewModel = viewModel(
        factory = MonthlyReportViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).budgetRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Monthly Report") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            MonthNavigationRow(
                monthLabel = formatMonthYear(uiState.selectedMonth),
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth
            )

            SummarySection(uiState)

            TopCategorySection(uiState.topCategory)

            CategoryBreakdownSection(uiState.categoryBreakdown)

            BudgetSummarySection(uiState.budgets)

            TransactionCountsSection(
                incomeCount = uiState.incomeTransactionCount,
                expenseCount = uiState.expenseTransactionCount
            )

            TextButton(
                onClick = {
                    val shareText = buildMonthlyReportShareText(uiState)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share report"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share report")
            }
        }
    }
}

@Composable
private fun MonthNavigationRow(
    monthLabel: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthNavigationButton(glyph = "←", contentDescription = "Previous month", onClick = onPreviousMonth)
        Text(monthLabel, style = MaterialTheme.typography.titleLarge)
        MonthNavigationButton(glyph = "→", contentDescription = "Next month", onClick = onNextMonth)
    }
}

// Both previous/next buttons render through this single composable so their size, shape,
// padding, elevation, and text style can never drift apart - the visual inconsistency reported
// after manual testing came from two separate plain TextButtons with only a glyph as content,
// which has no guaranteed shared touch-target or icon size. FilledTonalIconButton is a stock
// Material 3 component already available via the existing material3 dependency (no icon library
// needed - the glyph is plain Text sized to match, not a vector asset).
@Composable
private fun MonthNavigationButton(glyph: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription }
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SummarySection(uiState: MonthlyReportUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ReportSummaryCard(
                title = "Income",
                amountCents = uiState.incomeCents,
                amountColor = MaterialTheme.colorScheme.primary,
                changeCents = uiState.incomeChangeCents,
                modifier = Modifier.weight(1f)
            )
            ReportSummaryCard(
                title = "Expenses",
                amountCents = uiState.expenseCents,
                amountColor = MaterialTheme.colorScheme.error,
                changeCents = uiState.expenseChangeCents,
                modifier = Modifier.weight(1f)
            )
        }
        ReportBalanceCard(balanceCents = uiState.balanceCents, changeCents = uiState.balanceChangeCents)
    }
}

@Composable
private fun ReportSummaryCard(title: String, amountCents: Long, amountColor: Color, changeCents: Long, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAmountInCents(amountCents), style = MaterialTheme.typography.titleLarge, color = amountColor)
            MonthOverMonthText(changeCents)
        }
    }
}

@Composable
private fun ReportBalanceCard(balanceCents: Long, changeCents: Long, modifier: Modifier = Modifier) {
    val color = when {
        balanceCents > 0L -> MaterialTheme.colorScheme.primary
        balanceCents < 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (balanceCents < 0L) "-" else ""
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$sign${formatAmountInCents(abs(balanceCents))}", style = MaterialTheme.typography.headlineMedium, color = color)
            MonthOverMonthText(changeCents)
        }
    }
}

// Minimal month-over-month indicator, matching HomeScreen's summary cards.
@Composable
private fun MonthOverMonthText(changeCents: Long, modifier: Modifier = Modifier) {
    val sign = when {
        changeCents > 0L -> "+"
        changeCents < 0L -> "-"
        else -> ""
    }
    Text(
        "$sign${formatAmountInCents(abs(changeCents))} vs last month",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun TopCategorySection(topCategory: CategorySpending?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Top spending category", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            if (topCategory == null) {
                Text(
                    "No expenses recorded this month.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatCategoryDisplayName(topCategory.categoryName), style = MaterialTheme.typography.bodyLarge)
                    Text(formatAmountInCents(topCategory.totalCents), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownSection(categories: List<CategorySpending>, modifier: Modifier = Modifier) {
    val spentCategories = categories.filter { it.totalCents > 0L }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Category breakdown", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            if (spentCategories.isEmpty()) {
                Text(
                    "No expenses recorded this month.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    spentCategories.forEach { category -> CategoryBreakdownRow(category) }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(category: CategorySpending, modifier: Modifier = Modifier) {
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
private fun BudgetSummarySection(budgets: List<BudgetProgress>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Budget summary", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            if (budgets.isEmpty()) {
                Text(
                    "No budgets set for this month.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    budgets.forEachIndexed { index, progress ->
                        BudgetSummaryRow(progress)
                        if (index < budgets.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetSummaryRow(progress: BudgetProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatCategoryDisplayName(progress.categoryName), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${formatAmountInCents(progress.spentCents)} of ${formatAmountInCents(progress.limitCents)}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        LinearProgressIndicator(
            progress = { progress.clampedProgressFraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (progress.isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        if (progress.isExceeded) {
            Text(
                "Over budget by ${formatAmountInCents(-progress.remainingCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                "Remaining: ${formatAmountInCents(progress.remainingCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionCountsSection(incomeCount: Int, expenseCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Transactions", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Income: $incomeCount")
                Text("Expenses: $expenseCount")
            }
        }
    }
}
