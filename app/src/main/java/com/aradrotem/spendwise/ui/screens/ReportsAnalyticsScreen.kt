package com.aradrotem.spendwise.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import com.aradrotem.spendwise.domain.CategoryExpenseDetails
import com.aradrotem.spendwise.domain.CategorySpendingPoint
import com.aradrotem.spendwise.domain.DisplayedCategoryTransaction
import com.aradrotem.spendwise.domain.FinancialInsight
import com.aradrotem.spendwise.domain.MonthEndProjection
import com.aradrotem.spendwise.domain.MonthlySpendingPoint
import com.aradrotem.spendwise.domain.TopPayeePoint
import com.aradrotem.spendwise.domain.WeekdaySpendingPoint
import com.aradrotem.spendwise.ui.components.DonutChart
import com.aradrotem.spendwise.ui.components.DonutSegment
import com.aradrotem.spendwise.ui.components.IncomeExpenseBarChart
import com.aradrotem.spendwise.ui.components.LabeledBarChart
import com.aradrotem.spendwise.ui.components.SingleSeriesBarChart
import java.time.format.TextStyle
import java.util.Locale
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatDate
import com.aradrotem.spendwise.ui.format.formatMonthYear
import com.aradrotem.spendwise.ui.format.signedAbsolute
import com.aradrotem.spendwise.util.colorForCategory
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsAnalyticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsAnalyticsViewModel = viewModel(
        factory = ReportsAnalyticsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.categoryRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.budgetRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reports & analytics") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                },
                actions = {
                    if (!uiState.isLoading) {
                        TextButton(onClick = {
                            val shareText = buildShareText(uiState)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share report"))
                        }) { Text(stringResource(R.string.action_share)) }
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
            HeaderSection(uiState, viewModel)

            if (uiState.isSingleMonth) {
                uiState.monthly?.let { monthly -> MonthlyModeSections(monthly, uiState.monthlyInsights, uiState, viewModel) }
            } else {
                uiState.period?.let { period -> PeriodModeSections(period, uiState, viewModel) }
            }
        }
    }
}

// Builds the share text appropriate to the current mode - reuses buildMonthlyReportShareText/
// buildPeriodShareText exactly, no separate calculation path.
private fun buildShareText(uiState: ReportsAnalyticsUiState): String =
    if (uiState.isSingleMonth) {
        uiState.monthly?.let(::buildMonthlyReportShareText) ?: ""
    } else {
        uiState.period?.let(::buildPeriodShareText) ?: ""
    }

@Composable
private fun HeaderSection(uiState: ReportsAnalyticsUiState, viewModel: ReportsAnalyticsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthNavButton(glyph = "←", contentDescription = "Previous month", onClick = viewModel::onPreviousMonth)
            Text(formatMonthYear(uiState.selectedMonth), style = MaterialTheme.typography.titleLarge)
            MonthNavButton(glyph = "→", contentDescription = "Next month", onClick = viewModel::onNextMonth)
        }
        TimeRangeSelector(selected = uiState.timeRange, onSelect = viewModel::onTimeRangeChange)
    }
}

// Root cause of the missing "12 months" option on a Samsung Galaxy S22+: a plain, non-scrollable
// Row laying out four full-word buttons ("Month", "3 months", "6 months", "12 months") overflowed
// the screen width with no way to reach the last one. Fixed with compact "1M/3M/6M/12M" labels
// (each option's full meaning preserved via contentDescription for accessibility) plus a
// horizontally scrollable Row as a second line of defense against any width constraint (larger
// font scaling, narrower devices, etc).
// Not private: exercised directly (without an Application/database dependency) by
// ReportsAnalyticsTimeRangeSelectorTest, mirroring GroupSummaryCard/GroupExpenseRow.
@Composable
internal fun TimeRangeSelector(selected: AnalyticsTimeRange, onSelect: (AnalyticsTimeRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnalyticsTimeRange.entries.forEach { range ->
            TimeRangeOption(range, selected = selected == range, onClick = { onSelect(range) })
        }
    }
}

@Composable
private fun TimeRangeOption(range: AnalyticsTimeRange, selected: Boolean, onClick: () -> Unit) {
    val compactLabel = when (range) {
        AnalyticsTimeRange.SELECTED_MONTH -> "1M"
        AnalyticsTimeRange.LAST_3_MONTHS -> "3M"
        AnalyticsTimeRange.LAST_6_MONTHS -> "6M"
        AnalyticsTimeRange.LAST_12_MONTHS -> "12M"
    }
    val modifier = Modifier.semantics {
        contentDescription = range.label
        this.selected = selected
    }
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(compactLabel) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(compactLabel) }
    }
}

@Composable
private fun MonthNavButton(glyph: String, contentDescription: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.semantics { this.contentDescription = contentDescription }) {
        Text(glyph, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------------------------------------------------------------------------------------
// Monthly mode (AnalyticsTimeRange.SELECTED_MONTH)
// ---------------------------------------------------------------------------------------------

@Composable
private fun MonthlyModeSections(monthly: MonthlyReportUiState, insights: List<FinancialInsight>, uiState: ReportsAnalyticsUiState, viewModel: ReportsAnalyticsViewModel) {
    MonthlySummarySection(monthly)
    SpendingByCategorySection(
        points = CategoryDistributionFromBreakdown(monthly),
        emptyMessage = stringResource(R.string.empty_state_no_expenses_this_month),
        selectedCategory = uiState.selectedDrillDownCategory,
        details = uiState.drillDownDetails,
        onSelectCategory = viewModel::onCategorySelected
    )
    MonthlyBudgetSection(monthly.budgets)
    InsightsSection(insights)
    TransactionCountsSection(monthly.incomeTransactionCount, monthly.expenseTransactionCount)
    MultiMonthOnlyHint()
}

// Reconstructs CategorySpendingPoint from MonthlyReportUiState.categoryBreakdown so the merged
// "Spending by category" section (donut + list) is identical in monthly and period mode, without
// requiring MonthlyReportUiState itself to change shape.
private fun CategoryDistributionFromBreakdown(monthly: MonthlyReportUiState): List<CategorySpendingPoint> =
    monthly.categoryBreakdown
        .filter { it.totalCents > 0L }
        .map { entry ->
            CategorySpendingPoint(
                categoryName = entry.categoryName,
                amountCents = entry.totalCents,
                transactionCount = 0,
                percentage = entry.percentOfExpenses * 100f
            )
        }
        .sortedByDescending { it.amountCents }

@Composable
private fun MonthlySummarySection(monthly: MonthlyReportUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.label_summary), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryValueCard("Income", formatAmountInCents(monthly.incomeCents), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryValueCard("Expenses", formatAmountInCents(monthly.expenseCents), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        val balanceColor = if (monthly.balanceCents < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        SummaryValueCard("Balance", signedAbsolute(monthly.balanceCents), balanceColor, Modifier.fillMaxWidth())
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(monthOverMonthText("Income", monthly.incomeCents, monthly.previousMonthIncomeCents), style = MaterialTheme.typography.bodySmall)
                Text(monthOverMonthText("Expenses", monthly.expenseCents, monthly.previousMonthExpenseCents), style = MaterialTheme.typography.bodySmall)
                Text(monthOverMonthText("Balance", monthly.balanceCents, monthly.previousMonthBalanceCents), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MonthlyBudgetSection(budgets: List<BudgetProgress>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Budget performance", style = MaterialTheme.typography.titleMedium)
        if (budgets.isEmpty()) {
            EmptyStateCard("No budgets set for this month.")
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    budgets.forEachIndexed { index, progress ->
                        MonthlyBudgetRow(progress)
                        if (index < budgets.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyBudgetRow(progress: BudgetProgress) {
    val statusColor = when {
        progress.isExceeded -> MaterialTheme.colorScheme.error
        progress.clampedProgressFraction >= 0.9f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatCategoryDisplayName(progress.categoryName), style = MaterialTheme.typography.bodyLarge)
            Text("${formatAmountInCents(progress.spentCents)} of ${formatAmountInCents(progress.limitCents)}", style = MaterialTheme.typography.bodyLarge)
        }
        LinearProgressIndicator(progress = { progress.clampedProgressFraction }, modifier = Modifier.fillMaxWidth(), color = statusColor)
        val statusText = if (progress.isExceeded) "Over budget by ${formatAmountInCents(-progress.remainingCents)}" else "Remaining: ${formatAmountInCents(progress.remainingCents)}"
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
    }
}

@Composable
private fun TransactionCountsSection(incomeCount: Int, expenseCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Transactions", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Income: $incomeCount")
                Text("Expenses: $expenseCount")
            }
        }
    }
}

// Single-month mode never shows the multi-month-only charts, but explains why in one compact line
// rather than large empty placeholder cards.
@Composable
private fun MultiMonthOnlyHint() {
    Text(
        "Select 3, 6, or 12 months to view income and expense trends, monthly spending trend, and category trend.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---------------------------------------------------------------------------------------------
// Period mode (LAST_3/6/12_MONTHS)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PeriodModeSections(period: VisualAnalyticsUiState, uiState: ReportsAnalyticsUiState, viewModel: ReportsAnalyticsViewModel) {
    PeriodSummarySection(period)
    SpendingByCategorySection(
        points = period.categoryDistribution,
        emptyMessage = "No expenses recorded in this period.",
        selectedCategory = uiState.selectedDrillDownCategory,
        details = uiState.drillDownDetails,
        onSelectCategory = viewModel::onCategorySelected
    )
    IncomeVsExpenseSection(period)
    MonthlyTrendSection(period)
    CategoryTrendSection(period, viewModel)
    BudgetUtilizationSection(period)
    CumulativeSpendingSection(period)
    WeekdaySpendingSection(period)
    TopPayeesSection(period)
    MonthEndProjectionSection(period)
    InsightsSection(period.insights)
}

@Composable
private fun BudgetUtilizationSection(period: VisualAnalyticsUiState) {
    if (period.budgetActuals.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Budget utilization (${formatMonthYear(period.selectedMonth)})", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                period.budgetActuals.forEach { point ->
                    val overBudget = point.remainingCents < 0L
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatCategoryDisplayName(point.categoryName))
                            Text(
                                "${(point.usageFraction * 100).roundToInt()}%",
                                color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        LinearProgressIndicator(
                            progress = { point.usageFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CumulativeSpendingSection(period: VisualAnalyticsUiState) {
    if (period.cumulativeSpending.isEmpty()) return
    // Sampled at weekly-ish milestones so the bar chart stays readable instead of one bar per day.
    val sampleDays = listOf(7, 14, 21, 28, period.cumulativeSpending.size)
        .filter { it <= period.cumulativeSpending.size }
        .distinct()
    val samples = sampleDays.map { day -> period.cumulativeSpending[day - 1] }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cumulative spending (${formatMonthYear(period.selectedMonth)})", style = MaterialTheme.typography.titleMedium)
        LabeledBarChart(
            labels = samples.map { "Day ${it.day}" },
            valuesCents = samples.map { it.cumulativeExpenseCents },
            barColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun WeekdaySpendingSection(period: VisualAnalyticsUiState) {
    if (period.weekdaySpending.all { it.expenseCents == 0L }) return
    val highest = period.weekdaySpending.filter { it.expenseCents > 0L }.maxByOrNull { it.expenseCents }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Spending by day of week", style = MaterialTheme.typography.titleMedium)
        LabeledBarChart(
            labels = period.weekdaySpending.map { weekdayShortLabel(it) },
            valuesCents = period.weekdaySpending.map { it.expenseCents },
            barColor = MaterialTheme.colorScheme.secondary
        )
        highest?.let {
            Text(
                "You tend to spend the most on ${weekdayFullLabel(it)}.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Kept as plain (non-@Composable) top-level functions - java.time's Locale.getDefault() is a
// non-observable read that lint (NonObservableLocale) flags when called directly inside a
// composable, but is fine here since these are ordinary functions evaluated once per call.
private fun weekdayShortLabel(point: WeekdaySpendingPoint): String =
    point.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun weekdayFullLabel(point: WeekdaySpendingPoint): String =
    point.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
private fun TopPayeesSection(period: VisualAnalyticsUiState) {
    if (period.topPayees.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Top payees", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                period.topPayees.forEachIndexed { index, payee ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${payee.name}")
                        Text(formatAmountInCents(payee.totalCents))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthEndProjectionSection(period: VisualAnalyticsUiState) {
    val projection = period.monthEndProjection ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Month-end projection", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (projection.isReliable) {
                    Text("At your current pace, you are projected to spend ${formatAmountInCents(projection.projectedTotalCents)} this month.")
                    if (projection.projectedToExceedBudget) {
                        Text(
                            "Projected to exceed your monthly budget by ${formatAmountInCents(projection.projectedOverageCents)}.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "Not enough data yet this month for a reliable projection.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSummarySection(period: VisualAnalyticsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.label_summary), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryValueCard("Income", formatAmountInCents(period.totalIncomeCents), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryValueCard("Expenses", formatAmountInCents(period.totalExpenseCents), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            val balanceColor = if (period.netBalanceCents < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            SummaryValueCard("Balance", formatAmountInCents(period.netBalanceCents), balanceColor, Modifier.weight(1f))
            SummaryValueCard("Avg monthly expenses", formatAmountInCents(period.averageMonthlyExpenseCents), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Expense transactions: ${period.expenseTransactionCount}",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun IncomeVsExpenseSection(period: VisualAnalyticsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Income vs expenses", style = MaterialTheme.typography.titleMedium)
        IncomeExpenseBarChart(
            months = period.monthlyIncomeExpense.map { it.yearMonth },
            incomeCents = period.monthlyIncomeExpense.map { it.incomeCents },
            expenseCents = period.monthlyIncomeExpense.map { it.expenseCents }
        )
    }
}

@Composable
private fun MonthlyTrendSection(period: VisualAnalyticsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Monthly spending trend", style = MaterialTheme.typography.titleMedium)
        SingleSeriesBarChart(
            months = period.monthlySpendingTrend.map { it.yearMonth },
            valuesCents = period.monthlySpendingTrend.map { it.expenseCents },
            barColor = MaterialTheme.colorScheme.error
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonthlyTrendChangeText(period.monthlySpendingTrend.lastOrNull())
                period.highestSpendingMonth?.let { Text("Highest: ${formatMonthYear(it.yearMonth)} (${formatAmountInCents(it.expenseCents)})") }
                period.lowestSpendingMonth?.let { Text("Lowest: ${formatMonthYear(it.yearMonth)} (${formatAmountInCents(it.expenseCents)})") }
            }
        }
    }
}

@Composable
private fun MonthlyTrendChangeText(last: MonthlySpendingPoint?) {
    if (last == null) return
    val text = when {
        last.changePercent != null && last.changePercent > 0f -> "Expenses increased by ${roundedPercent(last.changePercent)}% vs previous month"
        last.changePercent != null && last.changePercent < 0f -> "Expenses decreased by ${roundedPercent(-last.changePercent)}% vs previous month"
        last.hasPreviousData && last.changePercent == null && last.expenseCents > 0L -> "Spending started this month"
        last.hasPreviousData -> "No previous spending for comparison"
        else -> null
    }
    text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

private fun roundedPercent(percent: Float): Float = (percent * 10).roundToInt() / 10f

@Composable
private fun CategoryTrendSection(period: VisualAnalyticsUiState, viewModel: ReportsAnalyticsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Category trend", style = MaterialTheme.typography.titleMedium)
        if (period.availableExpenseCategories.isEmpty()) {
            EmptyStateCard("No expense categories available yet.")
            return@Column
        }
        CategoryDropdown(
            categories = period.availableExpenseCategories,
            selected = period.selectedTrendCategory,
            onSelect = viewModel::onTrendCategorySelected
        )
        SingleSeriesBarChart(
            months = period.categoryTrend.map { it.yearMonth },
            valuesCents = period.categoryTrend.map { it.amountCents },
            barColor = period.selectedTrendCategory?.let { colorForCategory(it) } ?: MaterialTheme.colorScheme.primary
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val total = period.categoryTrend.sumOf { it.amountCents }
                val average = if (period.categoryTrend.isNotEmpty()) total / period.categoryTrend.size else 0L
                val highest = period.categoryTrend.maxByOrNull { it.amountCents }
                Text("Total: ${formatAmountInCents(total)}")
                Text("Average monthly: ${formatAmountInCents(average)}")
                highest?.let { Text("Highest: ${formatMonthYear(it.yearMonth)} (${formatAmountInCents(it.amountCents)})") }
            }
        }
    }
}

@Composable
private fun CategoryDropdown(categories: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.let { formatCategoryDisplayName(it) } ?: "Select category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(formatCategoryDisplayName(category)) },
                    onClick = { expanded = false; onSelect(category) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared sections (both modes)
// ---------------------------------------------------------------------------------------------

@Composable
private fun SummaryValueCard(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        }
    }
}

// One merged "Spending by category" section for both modes: donut chart + leading category +
// full textual breakdown, replacing Monthly Report's separate "Top category"/"Category breakdown"
// cards and Visual Analytics' separate distribution section - the same numbers are never shown in
// more than one place. Tapping a row (the donut chart itself has no separate legend to tap - see
// DonutChart) selects that category and expands an inline details card directly below the list -
// see ReportsAnalyticsViewModel.onCategorySelected for the tap-again-to-collapse behavior.
// Not private: exercised directly (without an Application/database dependency) by
// ReportsAnalyticsCategoryDrillDownTest, mirroring TimeRangeSelector/GroupSummaryCard.
@Composable
internal fun SpendingByCategorySection(
    points: List<CategorySpendingPoint>,
    emptyMessage: String,
    selectedCategory: String?,
    details: CategoryExpenseDetails?,
    onSelectCategory: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Spending by category", style = MaterialTheme.typography.titleMedium)
        if (points.isEmpty()) {
            EmptyStateCard(emptyMessage)
        } else {
            DonutChart(
                segments = points.map { DonutSegment(formatCategoryDisplayName(it.categoryName), it.amountCents, it.percentage, colorForCategory(it.categoryName)) }
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    points.forEach { point ->
                        CategoryBreakdownRow(
                            point = point,
                            isSelected = point.categoryName == selectedCategory,
                            onClick = { onSelectCategory(point.categoryName) }
                        )
                    }
                }
            }
            if (selectedCategory != null && details != null) {
                CategoryExpenseDetailsCard(details)
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(point: CategorySpendingPoint, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .padding(if (isSelected) 8.dp else 0.dp)
            .semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(12.dp).background(colorForCategory(point.categoryName), shape = CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Text(formatCategoryDisplayName(point.categoryName), style = MaterialTheme.typography.bodyLarge)
            if (point.transactionCount > 0) {
                Text(
                    "${point.transactionCount} transaction${if (point.transactionCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatAmountInCents(point.amountCents), style = MaterialTheme.typography.bodyLarge)
            Text("${point.percentage.roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Not color-only: a checkmark glyph makes the selected state legible without relying on
        // the highlighted background/border alone.
        if (isSelected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CategoryExpenseDetailsCard(details: CategoryExpenseDetails) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${details.categoryDisplayName} expenses", style = MaterialTheme.typography.titleSmall)
            Text(
                "${formatAmountInCents(details.totalAmountCents)} across ${details.transactionCount} transaction${if (details.transactionCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(details.periodLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (details.displayedTransactions.isEmpty()) {
                Text(
                    "No expenses found for this category in the selected period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider()
                details.displayedTransactions.forEachIndexed { index, transaction ->
                    CategoryExpenseTransactionRow(transaction)
                    if (index < details.displayedTransactions.lastIndex) HorizontalDivider()
                }
                if (details.isLimited) {
                    Text(
                        "Showing the ${details.displayedTransactions.size} largest of ${details.transactionCount} expenses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryExpenseTransactionRow(transaction: DisplayedCategoryTransaction) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                transaction.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatDate(transaction.timestampMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatAmountInCents(transaction.amountCents), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InsightsSection(insights: List<FinancialInsight>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Insights", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.forEach { insight -> Text("• ${insight.text}") }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
