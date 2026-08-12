package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.CategoryDao
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// In-memory CategoryDao for tests that only need a minimal, working category list (e.g. a
// ViewModel's availableCategories StateFlow) rather than testing category management itself.
class FakeCategoryDao : CategoryDao {
    private val rows = mutableListOf<CategoryEntity>()
    private var nextId = 1L

    override suspend fun insert(category: CategoryEntity): Long {
        val withId = category.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun insertIgnoringConflicts(categories: List<CategoryEntity>) {
        categories.forEach { category ->
            val exists = rows.any { it.normalizedName == category.normalizedName && it.type == category.type }
            if (!exists) rows += category.copy(id = nextId++)
        }
    }

    override suspend fun delete(category: CategoryEntity) {
        rows.removeAll { it.id == category.id }
    }

    override fun observeByType(type: TransactionType): Flow<List<CategoryEntity>> =
        flowOf(rows.filter { it.type == type })

    override suspend fun findByNormalizedNameAndType(normalizedName: String, type: TransactionType): CategoryEntity? =
        rows.firstOrNull { it.normalizedName == normalizedName && it.type == type }

    override suspend fun reassignTransactionsToOther(categoryName: String, type: TransactionType) = Unit

    override suspend fun deleteBudgetForCategory(categoryName: String) = Unit
}
