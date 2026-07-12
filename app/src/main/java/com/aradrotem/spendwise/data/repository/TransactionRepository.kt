package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.TransactionDao
import com.aradrotem.spendwise.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeBetween(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeBetween(startTimestamp, endTimestamp)

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun insert(transaction: TransactionEntity): Long = transactionDao.insert(transaction)

    suspend fun update(transaction: TransactionEntity) = transactionDao.update(transaction)

    suspend fun delete(transaction: TransactionEntity) = transactionDao.delete(transaction)
}
