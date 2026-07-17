package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Uses LegacyDatabaseV5/LegacyTransactionEntityV5/LegacyTransactionDaoV5 from Migration4To5Test.kt
// (same package): that frozen shape is the real version-5 schema, i.e. what's already installed
// on a physical device that ran the Step 10 migration-fix build.
@RunWith(AndroidJUnit4::class)
class Migration5To6Test {

    private val dbName = "migration-5-6-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate5To6_preservesExistingDataAndAddsOccurrenceExceptionSupport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        // Build a realistic "before" (v5) database: a manual transaction, a generated monthly
        // transaction with a sourceTitle snapshot, a generated installment transaction, a
        // recurring plan, a custom category, and a budget - matching what a real upgrading
        // device could plausibly already have.
        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV5::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV5(
                amountInCents = 4_500L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "groceries"
            )
        )
        val monthlyPlanId = legacyDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
                amountInCents = 5_000L, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 1
            )
        )
        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV5(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 2_000L,
                recurringPlanId = monthlyPlanId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03",
                sourceTitle = "Rent"
            )
        )
        val installmentPlanId = legacyDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.INSTALLMENT, title = "Laptop", categoryName = "SHOPPING",
                totalAmountInCents = 90_000L, totalInstallments = 3, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 15
            )
        )
        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV5(
                amountInCents = 30_000L, type = TransactionType.EXPENSE, category = "SHOPPING", timestamp = 3_000L,
                recurringPlanId = installmentPlanId, installmentNumber = 1, totalInstallments = 3,
                isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01", sourceTitle = "Laptop"
            )
        )
        legacyDb.categoryDao().insert(
            CategoryEntity(name = "Custom", normalizedName = "custom", type = TransactionType.EXPENSE, isBuiltIn = false)
        )
        legacyDb.budgetDao().insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 20_000L))
        legacyDb.close()

        // Reopen as the real (v6) database, applying only MIGRATION_5_6 - exactly as a real
        // device already at version 5 would upgrade.
        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first().sortedBy { it.timestamp }
        assertEquals(3, transactions.size)

        val manual = transactions[0]
        assertEquals(4_500L, manual.amountInCents)
        assertEquals("FOOD", manual.category)
        assertEquals("groceries", manual.note)
        assertFalse(manual.isOccurrenceModified)

        val generatedExpense = transactions[1]
        assertEquals(5_000L, generatedExpense.amountInCents)
        assertEquals(monthlyPlanId, generatedExpense.recurringPlanId)
        assertEquals("2026-03", generatedExpense.scheduledYearMonth)
        assertEquals("Rent", generatedExpense.sourceTitle)
        assertFalse(generatedExpense.isOccurrenceModified)

        val generatedInstallment = transactions[2]
        assertEquals(installmentPlanId, generatedInstallment.recurringPlanId)
        assertEquals(1, generatedInstallment.installmentNumber)
        assertEquals(3, generatedInstallment.totalInstallments)
        assertEquals("Laptop", generatedInstallment.sourceTitle)
        assertFalse(generatedInstallment.isOccurrenceModified)

        val categories = migratedDb.categoryDao().observeByType(TransactionType.EXPENSE).first()
        assertTrue(categories.any { it.name == "Custom" })

        val budgets = migratedDb.budgetDao().observeAll().first()
        assertEquals(1, budgets.size)

        val plans = migratedDb.recurringPaymentPlanDao().observeAll().first()
        assertEquals(2, plans.size)

        // The new recurring_occurrence_exceptions table exists and is usable through the real DAO.
        val exceptionDao = migratedDb.recurringOccurrenceExceptionDao()
        val firstInsertId = exceptionDao.insert(
            RecurringOccurrenceExceptionEntity(recurringPlanId = monthlyPlanId, scheduledYearMonth = "2026-04")
        )
        assertTrue(firstInsertId > 0)

        // The unique index on (recurringPlanId, scheduledYearMonth) survives the migration: a
        // duplicate insert is silently ignored (OnConflictStrategy.IGNORE), not a crash.
        val duplicateInsertId = exceptionDao.insert(
            RecurringOccurrenceExceptionEntity(recurringPlanId = monthlyPlanId, scheduledYearMonth = "2026-04")
        )
        assertEquals(-1L, duplicateInsertId)

        val exceptions = exceptionDao.getForPlan(monthlyPlanId)
        assertEquals(1, exceptions.size)
        assertEquals("2026-04", exceptions[0].scheduledYearMonth)
        assertEquals(OccurrenceExceptionType.SKIPPED, exceptions[0].exceptionType)

        // A pre-existing generated transaction (created before the migration) can now be marked
        // individually modified - the new column is usable on old rows, not just new ones.
        migratedDb.transactionDao().update(generatedExpense.copy(isOccurrenceModified = true))
        val updated = migratedDb.transactionDao().getById(generatedExpense.id)
        assertTrue(updated!!.isOccurrenceModified)

        migratedDb.close()
    }
}
