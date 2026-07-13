package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.CategoryEntity

data class CategoriesUiState(
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = true
)
