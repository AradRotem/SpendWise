package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncMetadataDao
import com.aradrotem.spendwise.data.sync.SyncStamper
import kotlinx.coroutines.flow.Flow

class RecurringOccurrenceExceptionRepository(
    private val dao: RecurringOccurrenceExceptionDao,
    syncMetadataDao: SyncMetadataDao? = null
) {
    private val syncStamper = SyncStamper(syncMetadataDao, SyncEntityType.RECURRING_EXCEPTION)

    fun observeAll(): Flow<List<RecurringOccurrenceExceptionEntity>> = dao.observeAll()

    suspend fun skipOccurrence(planId: Long, scheduledYearMonth: String) {
        val id = dao.insert(
            RecurringOccurrenceExceptionEntity(
                recurringPlanId = planId,
                scheduledYearMonth = scheduledYearMonth,
                exceptionType = OccurrenceExceptionType.SKIPPED
            )
        )
        // -1 means OnConflict IGNORE skipped a duplicate (already-skipped) row - nothing new to sync.
        if (id != -1L) syncStamper.markDirty(id)
    }

    // Fetched as a set, not a per-candidate query: the generator checks every due month for
    // every active plan on each run, so one query per plan is far cheaper than one per candidate.
    suspend fun getSkippedMonths(planId: Long): Set<String> =
        dao.getForPlan(planId).map { it.scheduledYearMonth }.toSet()
}
