package com.aradrotem.spendwise.sync

import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.sync.SyncEngine
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver
import com.aradrotem.spendwise.data.sync.SyncStatus
import com.aradrotem.spendwise.data.sync.adapters.RecurringExceptionSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.RecurringPlanSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.TransactionSyncAdapter
import com.aradrotem.spendwise.domain.FakeRecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.domain.FakeRecurringPaymentPlanDao
import com.aradrotem.spendwise.domain.FakeTransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression coverage for the real-device bug: "Sync error: Recurring plan for exception ...
// not yet available locally". Reproduces the exact adapter combination (RecurringPlanSyncAdapter
// + RecurringExceptionSyncAdapter, both driven by a real SyncEngine against a fake Firestore) so
// these tests exercise the production adapters, not just SyncEngine's generic defer plumbing
// (see SyncEngineTest for that).
class RecurringSyncOrderingTest {

    private val uid = "test-uid"

    private fun planDoc(status: RecurringPlanStatus = RecurringPlanStatus.ACTIVE, updatedAt: Long = 100L) = mapOf(
        "type" to RecurringPlanType.MONTHLY_RECURRING.name,
        "title" to "Rent",
        "categoryName" to "HOUSING",
        "note" to "",
        "amountInCents" to 500_000L,
        "totalAmountInCents" to null,
        "totalInstallments" to null,
        "firstPaymentDateMillis" to 1_000L,
        "preferredDayOfMonth" to 1,
        "endDateMillis" to null,
        "status" to status.name,
        "createdAt" to 1_000L,
        "updatedAt" to updatedAt
    )

    private fun exceptionDoc(planSyncId: String, month: String = "2026-08", updatedAt: Long = 100L) = mapOf(
        "recurringPlanSyncId" to planSyncId,
        "scheduledYearMonth" to month,
        "exceptionType" to OccurrenceExceptionType.SKIPPED.name,
        "createdAt" to 1_000L,
        "updatedAt" to updatedAt
    )

    @Test
    fun planAndExceptionBothRemote_exceptionResolvesCorrectly_regardlessOfArrivalOrder() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val firestore = FakeFirestoreSyncClient()
        val resolver = SyncIdResolver(metadataDao)
        val planDao = FakeRecurringPaymentPlanDao()
        val exceptionDao = FakeRecurringOccurrenceExceptionDao()
        val planAdapter = RecurringPlanSyncAdapter(planDao, uid)
        val exceptionAdapter = RecurringExceptionSyncAdapter(exceptionDao, resolver, uid)

        firestore.upsert(planAdapter.collectionPath, "plan-sync-1", planDoc())
        firestore.upsert(exceptionAdapter.collectionPath, "exception-sync-1", exceptionDoc("plan-sync-1"))

        val engine = SyncEngine(metadataDao, listOf(planAdapter, exceptionAdapter), firestore, FakeSyncWatermarkStore())
        engine.sync()

        assertTrue(engine.status.value is SyncStatus.Synced)
        assertEquals(1, planDao.allRows.size)
        assertEquals(1, exceptionDao.allRows.size)
        assertEquals(planDao.allRows.single().id, exceptionDao.allRows.single().recurringPlanId)
    }

    @Test
    fun exceptionArrivesBeforeItsParentPlan_deferredThenResolvedOnceParentIsAvailable() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val firestore = FakeFirestoreSyncClient()
        val resolver = SyncIdResolver(metadataDao)
        val planDao = FakeRecurringPaymentPlanDao()
        val exceptionDao = FakeRecurringOccurrenceExceptionDao()
        val planAdapter = RecurringPlanSyncAdapter(planDao, uid)
        val exceptionAdapter = RecurringExceptionSyncAdapter(exceptionDao, resolver, uid)
        val watermarks = FakeSyncWatermarkStore()
        val engine = SyncEngine(metadataDao, listOf(planAdapter, exceptionAdapter), firestore, watermarks)

        // Simulates the real-device scenario: the exception is already in Firestore, but this
        // device hasn't (yet) received its parent plan - e.g. a watermark/local-state mismatch,
        // or the plan simply hasn't propagated yet.
        firestore.upsert(exceptionAdapter.collectionPath, "exception-sync-1", exceptionDoc("plan-sync-1"))

        engine.sync()

        // No crash, no error status, and the exception is correctly deferred rather than dropped
        // or applied with a bogus parent id.
        assertTrue(engine.status.value is SyncStatus.Synced)
        assertEquals(0, exceptionDao.allRows.size)

        // The parent plan becomes available (e.g. a later push from another device).
        firestore.upsert(planAdapter.collectionPath, "plan-sync-1", planDoc(updatedAt = 200L))
        engine.sync()

        assertTrue(engine.status.value is SyncStatus.Synced)
        assertEquals(1, planDao.allRows.size)
        assertEquals(1, exceptionDao.allRows.size)
        assertEquals(planDao.allRows.single().id, exceptionDao.allRows.single().recurringPlanId)
    }

    @Test
    fun permanentlyMissingParent_neverCrashes_andUnrelatedEntitiesStillSync() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val firestore = FakeFirestoreSyncClient()
        val resolver = SyncIdResolver(metadataDao)
        val exceptionDao = FakeRecurringOccurrenceExceptionDao()
        val planAdapter = RecurringPlanSyncAdapter(FakeRecurringPaymentPlanDao(), uid)
        val exceptionAdapter = RecurringExceptionSyncAdapter(exceptionDao, resolver, uid)
        val transactionDao = FakeTransactionDao()
        val transactionAdapter = TransactionSyncAdapter(transactionDao, resolver, uid)
        val engine = SyncEngine(metadataDao, listOf(planAdapter, exceptionAdapter, transactionAdapter), firestore, FakeSyncWatermarkStore())

        // This plan syncId never appears in Firestore at all - a genuinely orphaned exception.
        firestore.upsert(exceptionAdapter.collectionPath, "exception-sync-1", exceptionDoc("plan-that-never-exists"))
        firestore.upsert(transactionAdapter.collectionPath, "tx-sync-1", mapOf("amountInCents" to 1_000L, "type" to "EXPENSE", "category" to "FOOD", "timestamp" to 1_000L, "note" to "unrelated", "updatedAt" to 100L))

        // Runs several times, mirroring repeated app-resume/connectivity/Sync-Now triggers -
        // deterministic and harmless every time, never resolves (correctly - there is nothing to
        // resolve to), never blocks the unrelated transaction from syncing.
        repeat(3) { engine.sync() }

        assertTrue(engine.status.value is SyncStatus.Synced)
        assertEquals(0, exceptionDao.allRows.size)
        assertEquals(1, transactionDao.allRows.size)
        assertEquals("unrelated", transactionDao.allRows.single().note)
    }

    @Test
    fun deferredRow_neverProducesErrorStatus_soIndependentSyncTriggersAreUnaffected() = runBlocking {
        // This is the condition SpendWiseApplication.triggerSync() relies on: personal SyncEngine
        // and SharedGroupSyncEngine are launched as two independent coroutines, but before this
        // fix a deferred row's uncaught exception would still have left SyncEngine's own status
        // stuck on Error even though nothing was actually broken - masking a benign situation.
        val metadataDao = FakeSyncMetadataDao()
        val firestore = FakeFirestoreSyncClient()
        val resolver = SyncIdResolver(metadataDao)
        val exceptionAdapter = RecurringExceptionSyncAdapter(FakeRecurringOccurrenceExceptionDao(), resolver, uid)
        val engine = SyncEngine(metadataDao, listOf(exceptionAdapter), firestore, FakeSyncWatermarkStore())
        firestore.upsert(exceptionAdapter.collectionPath, "exception-sync-1", exceptionDoc("plan-sync-never-arrives"))

        engine.sync()

        assertEquals(SyncStatus.Synced, engine.status.value)
    }
}
