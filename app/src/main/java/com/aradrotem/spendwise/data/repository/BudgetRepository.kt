package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.BudgetDao
import com.aradrotem.spendwise.data.local.BudgetEntity
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) {

    fun observeAll(): Flow<List<BudgetEntity>> = budgetDao.observeAll()

    suspend fun findByCategory(categoryName: String): BudgetEntity? = budgetDao.findByCategory(categoryName)

    suspend fun addBudget(categoryName: String, monthlyLimitCents: Long): Result<Unit> {
        if (monthlyLimitCents <= 0L) {
            return Result.failure(IllegalArgumentException("Monthly limit must be greater than zero"))
        }
        val existing = budgetDao.findByCategory(categoryName)
        if (existing != null) {
            return Result.failure(IllegalStateException("A budget for \"$categoryName\" already exists"))
        }
        budgetDao.insert(BudgetEntity(categoryName = categoryName, monthlyLimitCents = monthlyLimitCents))
        return Result.success(Unit)
    }

    suspend fun updateBudget(id: Long, categoryName: String, monthlyLimitCents: Long): Result<Unit> {
        if (monthlyLimitCents <= 0L) {
            return Result.failure(IllegalArgumentException("Monthly limit must be greater than zero"))
        }
        budgetDao.update(BudgetEntity(id = id, categoryName = categoryName, monthlyLimitCents = monthlyLimitCents))
        return Result.success(Unit)
    }

    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.delete(budget)
}
