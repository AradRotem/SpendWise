package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // One grouped query for all budgets' monthly spend (and the Home dashboard's top categories),
    // instead of one query per category. Half-open range: [startTimestamp, endTimestampExclusive).
    // Sorted highest-to-lowest so callers needing "top categories" don't need to re-sort.
    @Query(
        "SELECT category, SUM(amountInCents) AS totalCents FROM transactions " +
            "WHERE type = 'EXPENSE' AND timestamp >= :startTimestamp AND timestamp < :endTimestampExclusive " +
            "GROUP BY category ORDER BY totalCents DESC"
    )
    fun observeExpenseTotalsByCategory(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<CategoryMonthlyTotal>>

    // COALESCE keeps the result a safe zero instead of null when there are no matching rows.
    @Query(
        "SELECT COALESCE(SUM(amountInCents), 0) FROM transactions " +
            "WHERE type = :type AND timestamp >= :startTimestamp AND timestamp < :endTimestampExclusive"
    )
    fun observeTotalByType(type: TransactionType, startTimestamp: Long, endTimestampExclusive: Long): Flow<Long>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 5): Flow<List<TransactionEntity>>

    // Bulk-insert candidate generated payments, silently skipping any that already exist for
    // (recurringPlanId, scheduledYearMonth) thanks to the unique index. This is the real
    // duplicate guard: the generator's own due-date logic is not trusted as the sole gate, since
    // repeated/concurrent generation calls must still be safe. Room wraps a multi-row @Insert in
    // its own transaction, but @Transaction here makes that guarantee explicit.
    // Returns one row id per input, using -1 for entries that were ignored as duplicates.
    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGeneratedIgnoringConflicts(transactions: List<TransactionEntity>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE recurringPlanId = :planId AND scheduledYearMonth = :yearMonth)")
    suspend fun existsGeneratedPayment(planId: Long, yearMonth: String): Boolean

    @Query("SELECT COUNT(*) FROM transactions WHERE recurringPlanId = :planId AND isAutomaticallyGenerated = 1")
    suspend fun countGeneratedForPlan(planId: Long): Int

    @Query("SELECT * FROM transactions WHERE recurringPlanId = :planId ORDER BY scheduledYearMonth ASC")
    fun observeByPlan(planId: Long): Flow<List<TransactionEntity>>
}

data class CategoryMonthlyTotal(
    val category: String,
    val totalCents: Long
)
