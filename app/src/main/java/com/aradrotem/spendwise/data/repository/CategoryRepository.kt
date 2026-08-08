package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.BuiltInCategorySeed
import com.aradrotem.spendwise.data.local.CategoryDao
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.local.builtInCategorySeeds
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncMetadataDao
import com.aradrotem.spendwise.data.sync.SyncStamper
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val categoryDao: CategoryDao,
    syncMetadataDao: SyncMetadataDao? = null
) {
    // Only custom categories are ever synced - every account seeds its own identical set of
    // built-ins locally (see ensureBuiltInCategoriesSeeded), so there's nothing useful to upload
    // for those rows.
    private val syncStamper = SyncStamper(syncMetadataDao, SyncEntityType.CATEGORY)

    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>> = categoryDao.observeByType(type)

    suspend fun addCustomCategory(name: String, type: TransactionType): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Category name is required"))
        }
        val normalized = trimmed.lowercase()
        val existing = categoryDao.findByNormalizedNameAndType(normalized, type)
        if (existing != null) {
            return Result.failure(IllegalStateException("A category named \"$trimmed\" already exists"))
        }
        val id = categoryDao.insert(
            CategoryEntity(
                name = formatCategoryDisplayName(trimmed),
                normalizedName = normalized,
                type = type,
                isBuiltIn = false
            )
        )
        syncStamper.markDirty(id)
        return Result.success(Unit)
    }

    suspend fun deleteCustomCategory(category: CategoryEntity) {
        check(!category.isBuiltIn) { "Built-in categories cannot be deleted" }
        categoryDao.deleteCustomCategoryAndReassign(category)
        syncStamper.markDeleted(category.id)
    }

    // Idempotent: relies on the categories table's unique (normalizedName, type) index plus
    // OnConflictStrategy.IGNORE, so this is safe to call on every app start regardless of
    // whether the built-ins were already seeded by MIGRATION_1_2 or a prior run of this method.
    suspend fun ensureBuiltInCategoriesSeeded() {
        categoryDao.insertIgnoringConflicts(builtInCategorySeeds.map(BuiltInCategorySeed::toEntity))
    }
}

private fun BuiltInCategorySeed.toEntity(): CategoryEntity = CategoryEntity(
    name = name,
    normalizedName = normalizedName,
    type = TransactionType.valueOf(type),
    isBuiltIn = true
)
