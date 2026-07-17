package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeRecurringOccurrenceExceptionDao : RecurringOccurrenceExceptionDao {
    private val rows = mutableListOf<RecurringOccurrenceExceptionEntity>()
    private var nextId = 1L

    val allRows: List<RecurringOccurrenceExceptionEntity> get() = rows.toList()

    override suspend fun insert(exception: RecurringOccurrenceExceptionEntity): Long {
        val conflicts = rows.any {
            it.recurringPlanId == exception.recurringPlanId && it.scheduledYearMonth == exception.scheduledYearMonth
        }
        if (conflicts) return -1L
        val withId = exception.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun getForPlan(planId: Long): List<RecurringOccurrenceExceptionEntity> =
        rows.filter { it.recurringPlanId == planId }

    override fun observeAll(): Flow<List<RecurringOccurrenceExceptionEntity>> = flowOf(rows.toList())

    override suspend fun deleteForPlan(planId: Long) {
        rows.removeAll { it.recurringPlanId == planId }
    }
}
