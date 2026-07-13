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

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY isBuiltIn DESC, name COLLATE NOCASE ASC")
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE normalizedName = :normalizedName AND type = :type LIMIT 1")
    suspend fun findByNormalizedNameAndType(normalizedName: String, type: TransactionType): CategoryEntity?

    @Query("UPDATE transactions SET category = 'OTHER' WHERE category = :categoryName AND type = :type")
    suspend fun reassignTransactionsToOther(categoryName: String, type: TransactionType)

    @Transaction
    suspend fun deleteCustomCategoryAndReassign(category: CategoryEntity) {
        reassignTransactionsToOther(category.name, category.type)
        delete(category)
    }
}
