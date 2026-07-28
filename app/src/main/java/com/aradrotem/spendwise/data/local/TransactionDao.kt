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

    // Half-open range [startTimestamp, endTimestampExclusive), matching
    // observeExpenseTotalsByCategory/observeTotalByType - unlike observeBetween (inclusive on
    // both ends), this is safe to call with a month's [start, startOfNextMonth) boundary without
    // risking double-counting a next-month transaction dated exactly at midnight.
    @Query(
        "SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestampExclusive " +
            "ORDER BY timestamp DESC"
    )
    fun observeInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<TransactionEntity>>

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

    // "yyyy-MM" sorts correctly as a plain string, so no date parsing is needed here.
    @Query(
        "SELECT * FROM transactions WHERE recurringPlanId = :planId AND isAutomaticallyGenerated = 1 " +
            "AND scheduledYearMonth > :yearMonthExclusive ORDER BY scheduledYearMonth ASC"
    )
    suspend fun getGeneratedAfter(planId: Long, yearMonthExclusive: String): List<TransactionEntity>

    // Removes the selected occurrence and any later ones already generated for the same plan, in
    // one statement (">=" covers both, since the unique index guarantees at most one row per
    // plan+month).
    @Query(
        "DELETE FROM transactions WHERE recurringPlanId = :planId AND isAutomaticallyGenerated = 1 " +
            "AND scheduledYearMonth >= :yearMonthInclusive"
    )
    suspend fun deleteGeneratedFromMonth(planId: Long, yearMonthInclusive: String)

    // Brings already-generated, not-yet-reached later occurrences in line with a plan-level edit.
    // Rows the user already overrode individually (isOccurrenceModified = 1) are left untouched,
    // so a per-occurrence override always wins over a later bulk "and future" edit.
    @Query(
        "UPDATE transactions SET amountInCents = :amountInCents, category = :category, note = :note, sourceTitle = :sourceTitle " +
            "WHERE recurringPlanId = :planId AND isAutomaticallyGenerated = 1 AND isOccurrenceModified = 0 " +
            "AND scheduledYearMonth > :yearMonthExclusive"
    )
    suspend fun updateFutureGeneratedTransactionsWithAmount(
        planId: Long,
        yearMonthExclusive: String,
        amountInCents: Long,
        category: String,
        note: String,
        sourceTitle: String
    )

    // Same as above but for installment plans, where per-installment amounts must never be
    // rewritten retroactively - only safe metadata (category/note/title) is touched.
    @Query(
        "UPDATE transactions SET category = :category, note = :note, sourceTitle = :sourceTitle " +
            "WHERE recurringPlanId = :planId AND isAutomaticallyGenerated = 1 AND isOccurrenceModified = 0 " +
            "AND scheduledYearMonth > :yearMonthExclusive"
    )
    suspend fun updateFutureGeneratedTransactionsMetadataOnly(
        planId: Long,
        yearMonthExclusive: String,
        category: String,
        note: String,
        sourceTitle: String
    )
}

data class CategoryMonthlyTotal(
    val category: String,
    val totalCents: Long
)
