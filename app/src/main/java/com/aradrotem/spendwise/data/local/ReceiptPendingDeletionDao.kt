package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReceiptPendingDeletionDao {

    @Insert
    suspend fun insert(entry: ReceiptPendingDeletionEntity): Long

    @Query("SELECT * FROM receipt_pending_deletions")
    suspend fun getAll(): List<ReceiptPendingDeletionEntity>

    @Query("DELETE FROM receipt_pending_deletions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
