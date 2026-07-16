package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Uses LegacyDatabaseV3/LegacyTransactionEntityV3/LegacyTransactionDaoV3 from Migration3To4Test.kt
// (same package). Covers the full upgrade path for a device that never installed any Step 10
// build at all - straight from version 3 to version 5 in one open.
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    private val dbName = "migration-chain-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate3To5_fullChain_appliesBothMigrationsAndValidatesFinalSchema() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV3::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV3(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "groceries"
            )
        )
        legacyDb.close()

        // Applies MIGRATION_3_4 then MIGRATION_4_5 in one open. Room validates the resulting
        // schema against the real, current TransactionEntity/RecurringPaymentPlanEntity as soon
        // as it's queried below - if the two migrations together don't produce exactly what the
        // entities expect (including sourceTitle), this fails here.
        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals(5_000L, transactions[0].amountInCents)
        assertEquals("groceries", transactions[0].note)
        assertNull(transactions[0].sourceTitle)

        // Recurring-plan table and the version-5 transaction columns (including sourceTitle) are
        // all usable through the real DAOs.
        val planId = migratedDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.MONTHLY_SALARY, title = "Monthly salary", categoryName = "SALARY",
                amountInCents = 1_000_000L, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 1
            )
        )
        migratedDb.transactionDao().insertGeneratedIgnoringConflicts(
            listOf(
                TransactionEntity(
                    amountInCents = 1_000_000L, type = TransactionType.INCOME, category = "SALARY", timestamp = 2_000L,
                    recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03",
                    sourceTitle = "Monthly salary"
                )
            )
        )
        val salaryTransaction = migratedDb.transactionDao().observeByPlan(planId).first().single()
        assertEquals("Monthly salary", salaryTransaction.sourceTitle)
        assertTrue(salaryTransaction.type == TransactionType.INCOME)

        migratedDb.close()
    }
}
