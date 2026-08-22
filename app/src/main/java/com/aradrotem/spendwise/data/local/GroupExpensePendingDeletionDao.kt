package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GroupExpensePendingDeletionDao {
    @Insert
    suspend fun insert(entity: GroupExpensePendingDeletionEntity): Long

    @Query("SELECT * FROM group_expense_pending_deletions")
    suspend fun getAll(): List<GroupExpensePendingDeletionEntity>

    @Delete
    suspend fun delete(entity: GroupExpensePendingDeletionEntity)
}
