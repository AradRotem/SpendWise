package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanDao
import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// In-memory RecurringPaymentPlanDao used to unit-test RecurringPaymentGenerator and
// RecurringPaymentRepository without a real database.
class FakeRecurringPaymentPlanDao : RecurringPaymentPlanDao {
    private val rows = mutableListOf<RecurringPaymentPlanEntity>()
    private var nextId = 1L

    val allRows: List<RecurringPaymentPlanEntity> get() = rows.toList()

    override suspend fun insert(plan: RecurringPaymentPlanEntity): Long {
        val withId = plan.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun update(plan: RecurringPaymentPlanEntity) {
        val index = rows.indexOfFirst { it.id == plan.id }
        if (index >= 0) rows[index] = plan
    }

    override suspend fun delete(plan: RecurringPaymentPlanEntity) {
        rows.removeAll { it.id == plan.id }
    }

    override fun observeAll(): Flow<List<RecurringPaymentPlanEntity>> = flowOf(rows.toList())

    override suspend fun getActivePlans(): List<RecurringPaymentPlanEntity> =
        rows.filter { it.status == RecurringPlanStatus.ACTIVE }

    override suspend fun getById(id: Long): RecurringPaymentPlanEntity? = rows.firstOrNull { it.id == id }

    override suspend fun updateStatus(id: Long, status: RecurringPlanStatus) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(status = status)
    }
}
