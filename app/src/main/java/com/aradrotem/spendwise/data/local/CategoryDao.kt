package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflicts(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    // Alphabetical, case-insensitive, with the built-in OTHER category always last.
    @Query(
        "SELECT * FROM categories WHERE type = :type " +
            "ORDER BY CASE WHEN normalizedName = 'other' THEN 1 ELSE 0 END ASC, name COLLATE NOCASE ASC"
    )
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE normalizedName = :normalizedName AND type = :type LIMIT 1")
    suspend fun findByNormalizedNameAndType(normalizedName: String, type: TransactionType): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("UPDATE transactions SET category = 'OTHER' WHERE category = :categoryName AND type = :type")
    suspend fun reassignTransactionsToOther(categoryName: String, type: TransactionType)

    @Query("DELETE FROM budgets WHERE categoryName = :categoryName")
    suspend fun deleteBudgetForCategory(categoryName: String)

    @Transaction
    suspend fun deleteCustomCategoryAndReassign(category: CategoryEntity) {
        reassignTransactionsToOther(category.name, category.type)
        if (category.type == TransactionType.EXPENSE) {
            deleteBudgetForCategory(category.name)
        }
        delete(category)
    }
}
