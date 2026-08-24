package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.ExpenseGroupDao
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeExpenseGroupDao : ExpenseGroupDao {
    private val rows = mutableListOf<ExpenseGroupEntity>()
    private var nextId = 1L

    // Mirrors the real UNIQUE index on expense_groups.groupSyncId (see MIGRATION_13_14) - lets
    // tests exercise GroupExpenseRepository.getOrCreateLocalGroupForSync's insert-conflict
    // recovery path the same way the real database's constraint would trigger it.
    override suspend fun insert(group: ExpenseGroupEntity): Long {
        if (group.groupSyncId != null && rows.any { it.groupSyncId == group.groupSyncId }) {
            throw IllegalStateException("UNIQUE constraint failed: expense_groups.groupSyncId")
        }
        val withId = group.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun update(group: ExpenseGroupEntity) {
        val index = rows.indexOfFirst { it.id == group.id }
        if (index >= 0) rows[index] = group
    }

    override suspend fun delete(group: ExpenseGroupEntity) {
        rows.removeAll { it.id == group.id }
    }

    override fun observeAll(): Flow<List<ExpenseGroupEntity>> = flowOf(rows.toList())

    override suspend fun getById(id: Long): ExpenseGroupEntity? = rows.firstOrNull { it.id == id }

    override suspend fun getByGroupSyncId(groupSyncId: String): ExpenseGroupEntity? = rows.firstOrNull { it.groupSyncId == groupSyncId }
}
