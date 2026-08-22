package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupExpensePendingDeletionDao
import com.aradrotem.spendwise.data.local.GroupExpensePendingDeletionEntity

class FakeGroupExpensePendingDeletionDao : GroupExpensePendingDeletionDao {
    private val rows = mutableListOf<GroupExpensePendingDeletionEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: GroupExpensePendingDeletionEntity): Long {
        val withId = entity.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun getAll(): List<GroupExpensePendingDeletionEntity> = rows.toList()

    override suspend fun delete(entity: GroupExpensePendingDeletionEntity) {
        rows.removeAll { it.id == entity.id }
    }
}
