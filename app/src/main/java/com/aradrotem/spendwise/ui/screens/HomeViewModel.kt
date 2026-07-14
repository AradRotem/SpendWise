package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.util.currentMonthRange
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
        combine(
            transactionRepository.observeTotalByType(TransactionType.INCOME, range.startMillis, range.endExclusiveMillis),
            transactionRepository.observeTotalByType(TransactionType.EXPENSE, range.startMillis, range.endExclusiveMillis),
            transactionRepository.observeExpenseTotalsByCategory(range.startMillis, range.endExclusiveMillis),
            budgetRepository.observeAll(),
            transactionRepository.observeRecent()
        ) { income, expense, categoryTotals, budgets, recent ->
            val spentByCategory = categoryTotals.associateBy({ it.category }, { it.totalCents })

            val topCategories = categoryTotals
                .filter { it.totalCents > 0L }
                .take(3)
                .map { entry ->
                    CategorySpending(
                        categoryName = entry.category,
                        totalCents = entry.totalCents,
                        percentOfExpenses = if (expense > 0L) entry.totalCents.toFloat() / expense.toFloat() else 0f
                    )
                }

            HomeUiState(
                isLoading = false,
                monthlyIncomeCents = income,
                monthlyExpenseCents = expense,
                budgetOverview = BudgetOverview(
                    activeBudgetCount = budgets.size,
                    totalLimitCents = budgets.sumOf { it.monthlyLimitCents },
                    totalSpentCents = budgets.sumOf { spentByCategory[it.categoryName] ?: 0L }
                ),
                topCategories = topCategories,
                recentTransactions = recent
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
