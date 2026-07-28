package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatMonthYear
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class MonthlyReportViewModel(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now(zoneId))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlyReportUiState> = selectedMonth
        .flatMapLatest { month -> observeReportFor(month) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonthlyReportUiState(isLoading = true, selectedMonth = YearMonth.now(zoneId))
        )

    private fun observeReportFor(month: YearMonth) = run {
        val range = monthRange(month, zoneId)
        val previousRange = monthRange(month.minusMonths(1), zoneId)

        // combine() only has typed overloads up to 5 flows, so the 4 monthly amount flows and the
        // 3 detail flows are each combined separately first, then merged together - same pattern
        // as HomeViewModel.
        val monthlyAmountsFlow = combine(
            transactionRepository.observeTotalByType(TransactionType.INCOME, range.startMillis, range.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, range.startMillis, range.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.INCOME, previousRange.startMillis, previousRange.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, previousRange.startMillis, previousRange.endExclusiveMillis)
        ) { income, expense, previousIncome, previousExpense ->
            ReportMonthlyAmounts(income, expense, previousIncome, previousExpense)
        }

        val reportDetailsFlow = combine(
            transactionRepository.observeExpenseTotalsByCategory(range.startMillis, range.endExclusiveMillis),
            budgetRepository.observeAll(),
            transactionRepository.observeInRange(range.startMillis, range.endExclusiveMillis)
        ) { categoryTotals, budgets, transactionsInMonth ->
            ReportDetails(categoryTotals, budgets, transactionsInMonth)
        }

        combine(monthlyAmountsFlow, reportDetailsFlow) { amounts, details ->
            buildMonthlyReportUiState(
                selectedMonth = month,
                incomeCents = amounts.incomeCents,
                expenseCents = amounts.expenseCents,
                previousMonthIncomeCents = amounts.previousIncomeCents,
                previousMonthExpenseCents = amounts.previousExpenseCents,
                categoryTotals = details.categoryTotals,
                budgets = details.budgets,
                transactionsInMonth = details.transactionsInMonth
            )
        }
    }

    fun onPreviousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun onNextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    companion object {
        fun factory(transactionRepository: TransactionRepository, budgetRepository: BudgetRepository) = viewModelFactory {
            initializer { MonthlyReportViewModel(transactionRepository, budgetRepository) }
        }
    }
}

private data class ReportMonthlyAmounts(
    val incomeCents: Long,
    val expenseCents: Long,
    val previousIncomeCents: Long,
    val previousExpenseCents: Long
)

private data class ReportDetails(
    val categoryTotals: List<CategoryMonthlyTotal>,
    val budgets: List<BudgetEntity>,
    val transactionsInMonth: List<TransactionEntity>
)

// Pure transform from raw repository data to MonthlyReportUiState - kept as a top-level function
// (not a ViewModel member) so it's directly unit-testable without a Main dispatcher, mirroring
// buildHomeUiState in HomeViewModel.kt. Reuses computeBudgetProgress (BudgetsViewModel.kt) rather
// than recomputing budget spend/remaining/over-budget logic.
fun buildMonthlyReportUiState(
    selectedMonth: YearMonth,
    incomeCents: Long,
    expenseCents: Long,
    previousMonthIncomeCents: Long,
    previousMonthExpenseCents: Long,
    categoryTotals: List<CategoryMonthlyTotal>,
    budgets: List<BudgetEntity>,
    transactionsInMonth: List<TransactionEntity>
): MonthlyReportUiState {
    val categoryBreakdown = categoryTotals.map { entry ->
        CategorySpending(
            categoryName = entry.category,
            totalCents = entry.totalCents,
            percentOfExpenses = if (expenseCents > 0L) entry.totalCents.toFloat() / expenseCents.toFloat() else 0f
        )
    }

    return MonthlyReportUiState(
        isLoading = false,
        selectedMonth = selectedMonth,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        previousMonthIncomeCents = previousMonthIncomeCents,
        previousMonthExpenseCents = previousMonthExpenseCents,
        categoryBreakdown = categoryBreakdown,
        budgets = computeBudgetProgress(budgets, categoryTotals),
        incomeTransactionCount = transactionsInMonth.count { it.type == TransactionType.INCOME },
        expenseTransactionCount = transactionsInMonth.count { it.type == TransactionType.EXPENSE }
    )
}

// Pure text builder sharing the exact same values the report screen displays - there is no
// separate calculation path for the shared text. SpendWise only tracks transactions locally, so
// this never references transaction/plan ids or bank/card provider language.
fun buildMonthlyReportShareText(state: MonthlyReportUiState): String {
    val monthLabel = formatMonthYear(state.selectedMonth)
    val previousMonthLabel = formatMonthYear(state.selectedMonth.minusMonths(1))

    val lines = mutableListOf<String>()
    lines += "SpendWise Monthly Report — $monthLabel"
    lines += ""
    lines += "Income: ${formatAmountInCents(state.incomeCents)}"
    lines += "Expenses: ${formatAmountInCents(state.expenseCents)}"
    lines += "Balance: ${signedAbsolute(state.balanceCents)}"
    lines += ""
    lines += "Compared with $previousMonthLabel:"
    lines += "Income: ${signedDelta(state.incomeChangeCents)}"
    lines += "Expenses: ${signedDelta(state.expenseChangeCents)}"
    lines += "Balance: ${signedDelta(state.balanceChangeCents)}"

    state.topCategory?.let { top ->
        lines += ""
        lines += "Top spending category:"
        lines += "${formatCategoryDisplayName(top.categoryName)} — ${formatAmountInCents(top.totalCents)}"
    }

    val spentCategories = state.categoryBreakdown.filter { it.totalCents > 0L }
    lines += ""
    if (spentCategories.isNotEmpty()) {
        lines += "Category breakdown:"
        spentCategories.forEach { entry ->
            lines += "${formatCategoryDisplayName(entry.categoryName)}: ${formatAmountInCents(entry.totalCents)}"
        }
    } else {
        lines += "No expenses recorded this month."
    }

    if (state.budgets.isNotEmpty()) {
        lines += ""
        lines += "Budgets:"
        state.budgets.forEach { budget ->
            val base = "${formatCategoryDisplayName(budget.categoryName)}: ${formatAmountInCents(budget.spentCents)} of " +
                formatAmountInCents(budget.limitCents)
            lines += if (budget.isExceeded) "$base — over budget" else base
        }
    }

    lines += ""
    lines += "Transactions:"
    lines += "Income: ${state.incomeTransactionCount}"
    lines += "Expenses: ${state.expenseTransactionCount}"

    return lines.joinToString(separator = "\n")
}

// No sign for zero/positive (matches HomeScreen's BalanceCard), "-" only when negative.
private fun signedAbsolute(cents: Long): String {
    val sign = if (cents < 0L) "-" else ""
    return "$sign${formatAmountInCents(abs(cents))}"
}

// Explicit "+"/"-" for month-over-month deltas so a change is never ambiguous.
private fun signedDelta(cents: Long): String {
    val sign = when {
        cents > 0L -> "+"
        cents < 0L -> "-"
        else -> ""
    }
    return "$sign${formatAmountInCents(abs(cents))}"
}
