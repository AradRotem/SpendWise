package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import com.aradrotem.spendwise.domain.BudgetActualCalculator
import com.aradrotem.spendwise.domain.CategoryDistributionCalculator
import com.aradrotem.spendwise.domain.CategoryExpenseDetails
import com.aradrotem.spendwise.domain.CategoryExpenseDrillDownCalculator
import com.aradrotem.spendwise.domain.CategorySpendingPoint
import com.aradrotem.spendwise.domain.FinancialInsightGenerator
import com.aradrotem.spendwise.domain.MonthlyIncomeExpensePoint
import com.aradrotem.spendwise.domain.MonthlySpendingTrendCalculator
import com.aradrotem.spendwise.domain.monthsForAnalyticsRange
import com.aradrotem.spendwise.ui.format.formatMonthYear
import com.aradrotem.spendwise.ui.format.formatPeriodLabel
import com.aradrotem.spendwise.util.combinedRange
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

// Unified ViewModel for the merged "Reports & analytics" screen (replaces the separate
// MonthlyReportViewModel and VisualAnalyticsViewModel). Two data-fetching paths, chosen by the
// current AnalyticsTimeRange:
//  - SELECTED_MONTH: fetches the selected month's transactions plus the previous month's
//    aggregate totals (small `observeTotalByType` queries) - the exact same shape
//    MonthlyReportViewModel used to fetch - and reuses buildMonthlyReportUiState unchanged.
//  - LAST_3/6/12_MONTHS: fetches one combined-range transaction list (unchanged from
//    VisualAnalyticsViewModel) and reuses buildVisualAnalyticsUiState unchanged.
// Only (selectedMonth, timeRange) drive the outer flatMapLatest - so only a genuine month/range
// change cancels and restarts the underlying Room subscriptions. The category-trend selector and
// the category drill-down selector are combined *inside* each mode's flow instead: since they're
// plain in-memory MutableStateFlows with no Room access, combining them only re-invokes the pure
// builder function with the already-cached transaction list - it never re-queries Room and never
// opens a second subscription per category tap.
class ReportsAnalyticsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now(zoneId))
    private val timeRange = MutableStateFlow(AnalyticsTimeRange.SELECTED_MONTH)
    // Period-mode category-trend chart selection only; unused/ignored in monthly mode.
    private val selectedTrendCategory = MutableStateFlow<String?>(null)
    // "Spending by category" drill-down selection - shared by both modes, independent of the
    // trend selector above (a different feature: a chart-series pick vs. a transaction list).
    private val selectedDrillDownCategory = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReportsAnalyticsUiState> = combine(selectedMonth, timeRange) { month, range -> month to range }
        .flatMapLatest { (month, range) ->
            if (range == AnalyticsTimeRange.SELECTED_MONTH) {
                observeMonthlyMode(month, range)
            } else {
                observePeriodMode(month, range)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsAnalyticsUiState(isLoading = true)
        )

    private fun observeMonthlyMode(month: YearMonth, range: AnalyticsTimeRange) = run {
        val currentRange = monthRange(month, zoneId)
        val previousMonth = month.minusMonths(1)
        val previousRange = monthRange(previousMonth, zoneId)

        // combine() only has typed overloads up to 5 flows, so the 4 amount flows and the 3
        // detail flows are combined separately first, then merged - same pattern as the retired
        // MonthlyReportViewModel/HomeViewModel.
        val amountsFlow = combine(
            transactionRepository.observeTotalByType(TransactionType.INCOME, currentRange.startMillis, currentRange.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, currentRange.startMillis, currentRange.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.INCOME, previousRange.startMillis, previousRange.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, previousRange.startMillis, previousRange.endExclusiveMillis)
        ) { income, expense, previousIncome, previousExpense -> ReportMonthlyAmounts(income, expense, previousIncome, previousExpense) }

        val detailsFlow = combine(
            transactionRepository.observeExpenseTotalsByCategory(currentRange.startMillis, currentRange.endExclusiveMillis),
            budgetRepository.observeAll(),
            transactionRepository.observeInRange(currentRange.startMillis, currentRange.endExclusiveMillis)
        ) { categoryTotals, budgets, transactionsInMonth -> MonthlyDetails(categoryTotals, budgets, transactionsInMonth) }

        combine(amountsFlow, detailsFlow, selectedDrillDownCategory) { amounts, details, drillDownCategory ->
            buildMonthlyModeUiState(month, range, previousMonth, amounts, details, drillDownCategory)
        }
    }

    private fun observePeriodMode(month: YearMonth, range: AnalyticsTimeRange) = run {
        val months = monthsForAnalyticsRange(month, range)
        val combined = combinedRange(months.map { monthRange(it, zoneId) })

        combine(
            transactionRepository.observeInRange(combined.startMillis, combined.endExclusiveMillis),
            categoryRepository.observeByType(TransactionType.EXPENSE),
            budgetRepository.observeAll(),
            selectedTrendCategory,
            selectedDrillDownCategory
        ) { transactions, expenseCategories, budgets, trendCategory, drillDownCategory ->
            buildPeriodModeUiState(month, range, months, transactions, expenseCategories, budgets, trendCategory, drillDownCategory, zoneId)
        }
    }

    fun onPreviousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun onNextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    fun onTimeRangeChange(range: AnalyticsTimeRange) {
        timeRange.value = range
    }

    fun onTrendCategorySelected(categoryName: String) {
        selectedTrendCategory.value = categoryName
    }

    // Tapping the currently-selected category again collapses its details - chosen over "keep it
    // selected" since a tap that visibly does nothing reads as broken, while a toggle-to-collapse
    // is the more common and predictable pattern for an inline expandable section.
    fun onCategorySelected(categoryName: String) {
        selectedDrillDownCategory.update { current -> if (current == categoryName) null else categoryName }
    }

    companion object {
        fun factory(
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            budgetRepository: BudgetRepository
        ) = viewModelFactory {
            initializer { ReportsAnalyticsViewModel(transactionRepository, categoryRepository, budgetRepository) }
        }
    }
}

internal data class ReportMonthlyAmounts(
    val incomeCents: Long,
    val expenseCents: Long,
    val previousIncomeCents: Long,
    val previousExpenseCents: Long
)

internal data class MonthlyDetails(
    val categoryTotals: List<CategoryMonthlyTotal>,
    val budgets: List<BudgetEntity>,
    val transactionsInMonth: List<TransactionEntity>
)

// Pure transform for monthly mode - kept top-level so it's directly unit-testable without a Main
// dispatcher. Reuses buildMonthlyReportUiState unchanged for the "monthly" half of the state, then
// derives monthly-mode insights from a minimal 2-point (previous, current) trend so month-over-
// month insight wording works exactly like the multi-month case, without fetching more than the
// one extra previous-month aggregate MonthlyReportViewModel always fetched anyway.
internal fun buildMonthlyModeUiState(
    month: YearMonth,
    range: AnalyticsTimeRange,
    previousMonth: YearMonth,
    amounts: ReportMonthlyAmounts,
    details: MonthlyDetails,
    selectedDrillDownCategory: String?
): ReportsAnalyticsUiState {
    val monthlyState = buildMonthlyReportUiState(
        selectedMonth = month,
        incomeCents = amounts.incomeCents,
        expenseCents = amounts.expenseCents,
        previousMonthIncomeCents = amounts.previousIncomeCents,
        previousMonthExpenseCents = amounts.previousExpenseCents,
        categoryTotals = details.categoryTotals,
        budgets = details.budgets,
        transactionsInMonth = details.transactionsInMonth
    )

    val categoryDistribution = CategoryDistributionCalculator.calculate(details.transactionsInMonth)
    val twoPointHistory = listOf(
        MonthlyIncomeExpensePoint(previousMonth, amounts.previousIncomeCents, amounts.previousExpenseCents),
        MonthlyIncomeExpensePoint(month, amounts.incomeCents, amounts.expenseCents)
    )
    val trend = MonthlySpendingTrendCalculator.calculate(twoPointHistory)
    val budgetActuals = monthlyState.budgets.map { BudgetActualCalculator.calculate(it.categoryName, it.limitCents, it.spentCents) }

    val insights = FinancialInsightGenerator.generate(
        categoryPoints = categoryDistribution,
        monthlySpendingPoints = trend,
        monthlyIncomeExpensePoints = twoPointHistory,
        budgetActualPoints = budgetActuals,
        categoryTrendPoints = emptyList(),
        selectedCategoryName = null
    )

    val resolvedDrillDown = resolveDrillDown(
        selectedCategory = selectedDrillDownCategory,
        categoryDistribution = categoryDistribution,
        transactions = details.transactionsInMonth,
        periodLabel = formatMonthYear(month)
    )

    return ReportsAnalyticsUiState(
        isLoading = false,
        selectedMonth = month,
        timeRange = range,
        monthly = monthlyState,
        period = null,
        monthlyInsights = insights,
        selectedDrillDownCategory = resolvedDrillDown.selectedCategory,
        drillDownDetails = resolvedDrillDown.details
    )
}

// Pure transform for period mode - kept top-level so it's directly unit-testable without a Main
// dispatcher. Reuses buildVisualAnalyticsUiState unchanged for the "period" half of the state.
internal fun buildPeriodModeUiState(
    month: YearMonth,
    range: AnalyticsTimeRange,
    months: List<YearMonth>,
    transactions: List<TransactionEntity>,
    expenseCategories: List<CategoryEntity>,
    budgets: List<BudgetEntity>,
    selectedTrendCategory: String?,
    selectedDrillDownCategory: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): ReportsAnalyticsUiState {
    val periodState = buildVisualAnalyticsUiState(month, range, months, transactions, expenseCategories, budgets, selectedTrendCategory, zoneId)

    val resolvedDrillDown = resolveDrillDown(
        selectedCategory = selectedDrillDownCategory,
        categoryDistribution = periodState.categoryDistribution,
        transactions = transactions,
        periodLabel = formatPeriodLabel(months)
    )

    return ReportsAnalyticsUiState(
        isLoading = false,
        selectedMonth = month,
        timeRange = range,
        monthly = null,
        period = periodState,
        selectedDrillDownCategory = resolvedDrillDown.selectedCategory,
        drillDownDetails = resolvedDrillDown.details
    )
}

internal data class ResolvedDrillDown(val selectedCategory: String?, val details: CategoryExpenseDetails?)

// Shared by both modes. Auto-clears the selection (rather than falling back to some other
// category) the moment the selected category no longer appears in the current period's category
// distribution - e.g. the user changed the range/month, or edited/deleted/recategorized the last
// transaction that kept it there. This is a pure function of already-available data, so it never
// needs to react to anything beyond the inputs already flowing into buildMonthlyModeUiState/
// buildPeriodModeUiState.
internal fun resolveDrillDown(
    selectedCategory: String?,
    categoryDistribution: List<CategorySpendingPoint>,
    transactions: List<TransactionEntity>,
    periodLabel: String
): ResolvedDrillDown {
    if (selectedCategory == null) return ResolvedDrillDown(null, null)
    val stillPresent = categoryDistribution.any { it.categoryName == selectedCategory }
    if (!stillPresent) return ResolvedDrillDown(null, null)

    val details = CategoryExpenseDrillDownCalculator.calculate(
        category = selectedCategory,
        categoryDisplayName = formatCategoryDisplayName(selectedCategory),
        periodLabel = periodLabel,
        transactions = transactions
    )
    return ResolvedDrillDown(selectedCategory, details)
}
