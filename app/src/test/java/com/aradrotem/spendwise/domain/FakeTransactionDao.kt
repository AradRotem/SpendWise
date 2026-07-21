package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionDao
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// In-memory TransactionDao used to unit-test RecurringPaymentGenerator without a real database.
// insertGeneratedIgnoringConflicts mirrors the real unique index on
// (recurringPlanId, scheduledYearMonth): only rows where both are non-null participate in the
// uniqueness check, matching SQLite's "NULL is never equal to NULL" semantics.
class FakeTransactionDao : TransactionDao {
    private val rows = mutableListOf<TransactionEntity>()
    private var nextId = 1L

    val allRows: List<TransactionEntity> get() = rows.toList()

    override suspend fun insert(transaction: TransactionEntity): Long {
        val withId = transaction.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun update(transaction: TransactionEntity) {
        val index = rows.indexOfFirst { it.id == transaction.id }
        if (index >= 0) rows[index] = transaction
    }

    override suspend fun delete(transaction: TransactionEntity) {
        rows.removeAll { it.id == transaction.id }
    }

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(rows.toList())

    override fun observeBetween(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>> =
        flowOf(rows.filter { it.timestamp in startTimestamp..endTimestamp })

    override suspend fun getById(id: Long): TransactionEntity? = rows.firstOrNull { it.id == id }

    override suspend fun countByCategoryAndType(categoryName: String, type: TransactionType): Int =
        rows.count { it.category == categoryName && it.type == type }

    // Mirrors the real Room query: EXPENSE-only, half-open [start, endExclusive) range, grouped
    // by category and sorted highest-to-lowest.
    override fun observeExpenseTotalsByCategory(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<CategoryMonthlyTotal>> =
        flowOf(
            rows.asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.timestamp >= startTimestamp && it.timestamp < endTimestampExclusive }
                .groupBy { it.category }
                .map { (category, entries) -> CategoryMonthlyTotal(category, entries.sumOf { it.amountInCents }) }
                .sortedByDescending { it.totalCents }
        )

    // Mirrors the real Room query: half-open [start, endExclusive) range, COALESCE-style zero sum.
    override fun observeTotalByType(type: TransactionType, startTimestamp: Long, endTimestampExclusive: Long): Flow<Long> =
        flowOf(
            rows.filter { it.type == type && it.timestamp >= startTimestamp && it.timestamp < endTimestampExclusive }
                .sumOf { it.amountInCents }
        )

    override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = flowOf(rows.take(limit))

    override suspend fun insertGeneratedIgnoringConflicts(transactions: List<TransactionEntity>): List<Long> =
        transactions.map { candidate ->
            val conflicts = candidate.recurringPlanId != null && candidate.scheduledYearMonth != null &&
                rows.any {
                    it.recurringPlanId == candidate.recurringPlanId && it.scheduledYearMonth == candidate.scheduledYearMonth
                }
            if (conflicts) {
                -1L
            } else {
                val withId = candidate.copy(id = nextId++)
                rows += withId
                withId.id
            }
        }

    override suspend fun existsGeneratedPayment(planId: Long, yearMonth: String): Boolean =
        rows.any { it.recurringPlanId == planId && it.scheduledYearMonth == yearMonth }

    override suspend fun countGeneratedForPlan(planId: Long): Int =
        rows.count { it.recurringPlanId == planId && it.isAutomaticallyGenerated }

    override fun observeByPlan(planId: Long): Flow<List<TransactionEntity>> =
        flowOf(rows.filter { it.recurringPlanId == planId })

    override suspend fun getGeneratedAfter(planId: Long, yearMonthExclusive: String): List<TransactionEntity> =
        rows.filter {
            it.recurringPlanId == planId && it.isAutomaticallyGenerated &&
                it.scheduledYearMonth != null && it.scheduledYearMonth > yearMonthExclusive
        }.sortedBy { it.scheduledYearMonth }

    override suspend fun deleteGeneratedFromMonth(planId: Long, yearMonthInclusive: String) {
        rows.removeAll {
            it.recurringPlanId == planId && it.isAutomaticallyGenerated &&
                it.scheduledYearMonth != null && it.scheduledYearMonth >= yearMonthInclusive
        }
    }

    override suspend fun updateFutureGeneratedTransactionsWithAmount(
        planId: Long,
        yearMonthExclusive: String,
        amountInCents: Long,
        category: String,
        note: String,
        sourceTitle: String
    ) {
        applyFutureUpdate(planId, yearMonthExclusive) {
            it.copy(amountInCents = amountInCents, category = category, note = note, sourceTitle = sourceTitle)
        }
    }

    override suspend fun updateFutureGeneratedTransactionsMetadataOnly(
        planId: Long,
        yearMonthExclusive: String,
        category: String,
        note: String,
        sourceTitle: String
    ) {
        applyFutureUpdate(planId, yearMonthExclusive) {
            it.copy(category = category, note = note, sourceTitle = sourceTitle)
        }
    }

    private fun applyFutureUpdate(planId: Long, yearMonthExclusive: String, transform: (TransactionEntity) -> TransactionEntity) {
        for (index in rows.indices) {
            val row = rows[index]
            val eligible = row.recurringPlanId == planId && row.isAutomaticallyGenerated && !row.isOccurrenceModified &&
                row.scheduledYearMonth != null && row.scheduledYearMonth > yearMonthExclusive
            if (eligible) {
                rows[index] = transform(row)
            }
        }
    }
}
