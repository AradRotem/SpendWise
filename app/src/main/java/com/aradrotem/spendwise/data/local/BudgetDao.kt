package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets ORDER BY categoryName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE categoryName = :categoryName LIMIT 1")
    suspend fun findByCategory(categoryName: String): BudgetEntity?

    @Query("DELETE FROM budgets WHERE categoryName = :categoryName")
    suspend fun deleteByCategory(categoryName: String)
}
