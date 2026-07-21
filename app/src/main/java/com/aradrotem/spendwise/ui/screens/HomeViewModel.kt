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
import com.aradrotem.spendwise.util.currentMonthRange
import com.aradrotem.spendwise.util.previousMonthRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = run {
        val range = currentMonthRange()
        val previousRange = previousMonthRange()

        // combine() only has typed overloads up to 5 flows, so the 4 monthly amount flows and the
        // 3 current-month detail flows are each combined separately first, then merged together.
        val monthlyAmountsFlow = combine(
            transactionRepository.observeTotalByType(TransactionType.INCOME, range.startMillis, range.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, range.startMillis, range.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.INCOME, previousRange.startMillis, previousRange.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, previousRange.startMillis, previousRange.endExclusiveMillis)
        ) { income, expense, previousIncome, previousExpense ->
            MonthlyAmounts(income, expense, previousIncome, previousExpense)
        }

        val dashboardDetailsFlow = combine(
            transactionRepository.observeExpenseTotalsByCategory(range.startMillis, range.endExclusiveMillis),
            budgetRepository.observeAll(),
            transactionRepository.observeRecent()
        ) { categoryTotals, budgets, recent ->
            DashboardDetails(categoryTotals, budgets, recent)
        }

        combine(monthlyAmountsFlow, dashboardDetailsFlow) { amounts, details ->
            buildHomeUiState(
                incomeCents = amounts.incomeCents,
                expenseCents = amounts.expenseCents,
                previousMonthIncomeCents = amounts.previousIncomeCents,
                previousMonthExpenseCents = amounts.previousExpenseCents,
                categoryTotals = details.categoryTotals,
                budgets = details.budgets,
                recentTransactions = details.recentTransactions
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    companion object {
        fun factory(transactionRepository: TransactionRepository, budgetRepository: BudgetRepository) = viewModelFactory {
            initializer { HomeViewModel(transactionRepository, budgetRepository) }
        }
    }
}

private data class MonthlyAmounts(
    val incomeCents: Long,
    val expenseCents: Long,
    val previousIncomeCents: Long,
    val previousExpenseCents: Long
)

private data class DashboardDetails(
    val categoryTotals: List<CategoryMonthlyTotal>,
    val budgets: List<BudgetEntity>,
    val recentTransactions: List<TransactionEntity>
)

// Pure transform from raw repository data to HomeUiState - kept as a top-level function (not a
// ViewModel member) so it's directly unit-testable without a Main dispatcher, mirroring
// resolveGeneratedTransactionActionInfo in TransactionsViewModel.
fun buildHomeUiState(
    incomeCents: Long,
    expenseCents: Long,
    previousMonthIncomeCents: Long,
    previousMonthExpenseCents: Long,
    categoryTotals: List<CategoryMonthlyTotal>,
    budgets: List<BudgetEntity>,
    recentTransactions: List<TransactionEntity>
): HomeUiState {
    val spentByCategory = categoryTotals.associateBy({ it.category }, { it.totalCents })

    val topCategories = categoryTotals
        .filter { it.totalCents > 0L }
        .take(3)
        .map { entry ->
            CategorySpending(
                categoryName = entry.category,
                totalCents = entry.totalCents,
                percentOfExpenses = if (expenseCents > 0L) entry.totalCents.toFloat() / expenseCents.toFloat() else 0f
            )
        }

    return HomeUiState(
        isLoading = false,
        monthlyIncomeCents = incomeCents,
        monthlyExpenseCents = expenseCents,
        previousMonthIncomeCents = previousMonthIncomeCents,
        previousMonthExpenseCents = previousMonthExpenseCents,
        budgetOverview = BudgetOverview(
            activeBudgetCount = budgets.size,
            totalLimitCents = budgets.sumOf { it.monthlyLimitCents },
            totalSpentCents = budgets.sumOf { spentByCategory[it.categoryName] ?: 0L }
        ),
        topCategories = topCategories,
        recentTransactions = recentTransactions
    )
}
