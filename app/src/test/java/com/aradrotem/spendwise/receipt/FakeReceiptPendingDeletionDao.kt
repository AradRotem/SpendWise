package com.aradrotem.spendwise.receipt

import com.aradrotem.spendwise.data.local.ReceiptPendingDeletionDao
import com.aradrotem.spendwise.data.local.ReceiptPendingDeletionEntity

class FakeReceiptPendingDeletionDao : ReceiptPendingDeletionDao {
    private val rows = mutableListOf<ReceiptPendingDeletionEntity>()
    private var nextId = 1L

    override suspend fun insert(entry: ReceiptPendingDeletionEntity): Long {
        val withId = entry.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun getAll(): List<ReceiptPendingDeletionEntity> = rows.toList()

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }
}
