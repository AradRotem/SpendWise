package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionDao
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncMetadataDao
import com.aradrotem.spendwise.data.sync.SyncStamper
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    syncMetadataDao: SyncMetadataDao? = null
) {
    private val syncStamper = SyncStamper(syncMetadataDao, SyncEntityType.TRANSACTION)

    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeBetween(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeBetween(startTimestamp, endTimestamp)

    fun observeInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeInRange(startTimestamp, endTimestampExclusive)

    fun observeExpenseTotalsByCategory(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<CategoryMonthlyTotal>> =
        transactionDao.observeExpenseTotalsByCategory(startTimestamp, endTimestampExclusive)

    fun observeTotalByType(type: TransactionType, startTimestamp: Long, endTimestampExclusive: Long): Flow<Long> =
        transactionDao.observeTotalByType(type, startTimestamp, endTimestampExclusive)

    fun observeRecent(limit: Int = 5): Flow<List<TransactionEntity>> = transactionDao.observeRecent(limit)

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun countByCategoryAndType(categoryName: String, type: TransactionType): Int =
        transactionDao.countByCategoryAndType(categoryName, type)

    suspend fun insert(transaction: TransactionEntity): Long {
        val id = transactionDao.insert(transaction)
        syncStamper.markDirty(id)
        return id
    }

    suspend fun update(transaction: TransactionEntity) {
        transactionDao.update(transaction)
        syncStamper.markDirty(transaction.id)
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.delete(transaction)
        syncStamper.markDeleted(transaction.id)
    }

    fun observeByPlan(planId: Long): Flow<List<TransactionEntity>> = transactionDao.observeByPlan(planId)

    suspend fun countGeneratedForPlan(planId: Long): Int = transactionDao.countGeneratedForPlan(planId)

    suspend fun existsGeneratedPayment(planId: Long, yearMonth: String): Boolean =
        transactionDao.existsGeneratedPayment(planId, yearMonth)

    suspend fun insertGeneratedIgnoringConflicts(transactions: List<TransactionEntity>): List<Long> =
        transactionDao.insertGeneratedIgnoringConflicts(transactions)

    suspend fun getGeneratedAfter(planId: Long, yearMonthExclusive: String): List<TransactionEntity> =
        transactionDao.getGeneratedAfter(planId, yearMonthExclusive)

    suspend fun deleteGeneratedFromMonth(planId: Long, yearMonthInclusive: String) =
        transactionDao.deleteGeneratedFromMonth(planId, yearMonthInclusive)

    suspend fun updateFutureGeneratedTransactionsWithAmount(
        planId: Long,
        yearMonthExclusive: String,
        amountInCents: Long,
        category: String,
        note: String,
        sourceTitle: String
    ) = transactionDao.updateFutureGeneratedTransactionsWithAmount(planId, yearMonthExclusive, amountInCents, category, note, sourceTitle)

    suspend fun updateFutureGeneratedTransactionsMetadataOnly(
        planId: Long,
        yearMonthExclusive: String,
        category: String,
        note: String,
        sourceTitle: String
    ) = transactionDao.updateFutureGeneratedTransactionsMetadataOnly(planId, yearMonthExclusive, category, note, sourceTitle)
}
