package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.BudgetDao
import com.aradrotem.spendwise.data.local.BudgetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// In-memory BudgetDao used to unit-test budget/dashboard integration without a real database.
class FakeBudgetDao : BudgetDao {
    private val rows = mutableListOf<BudgetEntity>()
    private var nextId = 1L

    val allRows: List<BudgetEntity> get() = rows.toList()

    override suspend fun insert(budget: BudgetEntity): Long {
        val withId = budget.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun update(budget: BudgetEntity) {
        val index = rows.indexOfFirst { it.id == budget.id }
        if (index >= 0) rows[index] = budget
    }

    override suspend fun delete(budget: BudgetEntity) {
        rows.removeAll { it.id == budget.id }
    }

    override fun observeAll(): Flow<List<BudgetEntity>> =
        flowOf(rows.sortedBy { it.categoryName.lowercase() })

    override suspend fun findByCategory(categoryName: String): BudgetEntity? =
        rows.firstOrNull { it.categoryName == categoryName }

    override suspend fun getById(id: Long): BudgetEntity? = rows.firstOrNull { it.id == id }

    override suspend fun deleteByCategory(categoryName: String) {
        rows.removeAll { it.categoryName == categoryName }
    }
}
