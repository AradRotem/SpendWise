package com.aradrotem.spendwise.data.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyImportManagerTest {

    private val uidA = "test-uid-a"
    private val uidB = "test-uid-b"
    private lateinit var context: Context

    @After
    fun cleanUp() {
        context.deleteDatabase("spendwise.db")
        context.deleteDatabase("spendwise_$uidA.db")
        context.deleteDatabase("spendwise_$uidB.db")
        context.getSharedPreferences("spendwise_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SpendWiseDatabase.closeInstance(null)
        SpendWiseDatabase.closeInstance(uidA)
        SpendWiseDatabase.closeInstance(uidB)
    }

    private fun seedLegacyData() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cleanUp()
        val legacyDb = SpendWiseDatabase.getInstance(context, null)
        legacyDb.transactionDao().insert(
            TransactionEntity(amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L, note = "coffee")
        )
        legacyDb.budgetDao().insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 5_000L))
    }

    @Test
    fun importIfNeeded_copiesLegacyDataIntoNewAccountExactlyOnce() = runBlocking {
        seedLegacyData()
        val manager = LegacyImportManager(context)

        manager.importIfNeeded(uidA)

        val targetDb = SpendWiseDatabase.getInstance(context, uidA)
        assertEquals(1, targetDb.transactionDao().observeAll().first().size)
        assertEquals(1, targetDb.budgetDao().observeAll().first().size)

        // Re-running for the same, already-imported uid must not duplicate anything.
        manager.importIfNeeded(uidA)
        assertEquals(1, targetDb.transactionDao().observeAll().first().size)
        assertEquals(1, targetDb.budgetDao().observeAll().first().size)
    }

    @Test
    fun importIfNeeded_resumesCorrectlyAfterPartialInterruptedImport() = runBlocking {
        seedLegacyData()
        val manager = LegacyImportManager(context)

        // Simulate a crash that happened after the transaction was copied (and its bookkeeping
        // committed atomically) but before the budget was copied and before the completion flag
        // was ever written - i.e. legacy_import_map already has the transaction's row.
        val targetDb = SpendWiseDatabase.getInstance(context, uidA)
        val legacyTx = SpendWiseDatabase.getInstance(context, null).transactionDao().observeAll().first().first()
        val preCopiedId = targetDb.transactionDao().insert(legacyTx.copy(id = 0))
        targetDb.legacyImportRecordDao().insert(
            LegacyImportRecordEntity(entityType = SyncEntityType.TRANSACTION.tag, legacyLocalId = legacyTx.id, newLocalId = preCopiedId)
        )
        // Note: completion flag intentionally left unset, simulating the interruption.

        manager.importIfNeeded(uidA)

        // The pre-copied transaction must not be duplicated; the budget (not yet copied) must
        // still be imported by the resumed run.
        assertEquals(1, targetDb.transactionDao().observeAll().first().size)
        assertEquals(1, targetDb.budgetDao().observeAll().first().size)
    }

    @Test
    fun importIfNeeded_secondDifferentAccountGetsNoLegacyData() = runBlocking {
        seedLegacyData()
        val manager = LegacyImportManager(context)

        manager.importIfNeeded(uidA)
        manager.importIfNeeded(uidB)

        val targetDbB = SpendWiseDatabase.getInstance(context, uidB)
        assertEquals(0, targetDbB.transactionDao().observeAll().first().size)
        assertEquals(0, targetDbB.budgetDao().observeAll().first().size)
    }

    @Test
    fun importIfNeeded_emptyLegacyDatabaseCompletesWithoutError() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        cleanUp()
        val manager = LegacyImportManager(context)

        manager.importIfNeeded(uidA)

        val targetDb = SpendWiseDatabase.getInstance(context, uidA)
        assertEquals(0, targetDb.transactionDao().observeAll().first().size)
        assertNull(targetDb.legacyImportRecordDao().find(SyncEntityType.TRANSACTION.tag, 1L))
    }
}
