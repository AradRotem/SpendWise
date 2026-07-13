package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp " +
            "ORDER BY timestamp DESC"
    )
    fun observeBetween(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE category = :categoryName AND type = :type")
    suspend fun countByCategoryAndType(categoryName: String, type: TransactionType): Int
}
