package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionDao
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeBetween(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeBetween(startTimestamp, endTimestamp)

    fun observeExpenseTotalsByCategory(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<CategoryMonthlyTotal>> =
        transactionDao.observeExpenseTotalsByCategory(startTimestamp, endTimestampExclusive)

    fun observeTotalByType(type: TransactionType, startTimestamp: Long, endTimestampExclusive: Long): Flow<Long> =
        transactionDao.observeTotalByType(type, startTimestamp, endTimestampExclusive)

    fun observeRecent(limit: Int = 5): Flow<List<TransactionEntity>> = transactionDao.observeRecent(limit)

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun countByCategoryAndType(categoryName: String, type: TransactionType): Int =
        transactionDao.countByCategoryAndType(categoryName, type)

    suspend fun insert(transaction: TransactionEntity): Long = transactionDao.insert(transaction)

    suspend fun update(transaction: TransactionEntity) = transactionDao.update(transaction)

    suspend fun delete(transaction: TransactionEntity) = transactionDao.delete(transaction)

    fun observeByPlan(planId: Long): Flow<List<TransactionEntity>> = transactionDao.observeByPlan(planId)

    suspend fun countGeneratedForPlan(planId: Long): Int = transactionDao.countGeneratedForPlan(planId)

    suspend fun existsGeneratedPayment(planId: Long, yearMonth: String): Boolean =
        transactionDao.existsGeneratedPayment(planId, yearMonth)

    suspend fun insertGeneratedIgnoringConflicts(transactions: List<TransactionEntity>): List<Long> =
        transactionDao.insertGeneratedIgnoringConflicts(transactions)
}
