package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import kotlinx.coroutines.flow.Flow

class RecurringOccurrenceExceptionRepository(private val dao: RecurringOccurrenceExceptionDao) {

    fun observeAll(): Flow<List<RecurringOccurrenceExceptionEntity>> = dao.observeAll()

    suspend fun skipOccurrence(planId: Long, scheduledYearMonth: String) {
        dao.insert(
            RecurringOccurrenceExceptionEntity(
                recurringPlanId = planId,
                scheduledYearMonth = scheduledYearMonth,
                exceptionType = OccurrenceExceptionType.SKIPPED
            )
        )
    }

    // Fetched as a set, not a per-candidate query: the generator checks every due month for
    // every active plan on each run, so one query per plan is far cheaper than one per candidate.
    suspend fun getSkippedMonths(planId: Long): Set<String> =
        dao.getForPlan(planId).map { it.scheduledYearMonth }.toSet()
}
