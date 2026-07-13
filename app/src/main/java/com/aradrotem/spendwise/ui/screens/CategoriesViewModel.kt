package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.observeByType(TransactionType.INCOME),
        categoryRepository.observeByType(TransactionType.EXPENSE)
    ) { income, expense ->
        CategoriesUiState(incomeCategories = income, expenseCategories = expense, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoriesUiState(isLoading = true)
    )

    // Returns a validation error message, or null on success.
    suspend fun addCategory(name: String, type: TransactionType): String? =
        categoryRepository.addCustomCategory(name, type).exceptionOrNull()?.message

    suspend fun countTransactionsUsing(category: CategoryEntity): Int =
        transactionRepository.countByCategoryAndType(category.name, category.type)

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.deleteCustomCategory(category)
        }
    }

    companion object {
        fun factory(categoryRepository: CategoryRepository, transactionRepository: TransactionRepository) =
            viewModelFactory {
                initializer { CategoriesViewModel(categoryRepository, transactionRepository) }
            }
    }
}
