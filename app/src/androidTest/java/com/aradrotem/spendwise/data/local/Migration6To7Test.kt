package com.aradrotem.spendwise.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Version-6 schema is identical to the real (pre-Step-14) entities - none of them change shape in
// MIGRATION_6_7, which only adds new group-expense tables - so this reuses the real entity/DAO
// classes directly rather than freezing new Legacy* copies, unlike Migration5To6Test (whose
// TransactionEntity shape did change across versions).
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringPaymentPlanEntity::class,
        RecurringOccurrenceExceptionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LegacyDatabaseV6 : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringPaymentPlanDao(): RecurringPaymentPlanDao
    abstract fun recurringOccurrenceExceptionDao(): RecurringOccurrenceExceptionDao
}

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

    private val dbName = "migration-6-7-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate6To7_preservesExistingDataAndAddsGroupExpenseSupport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV6::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        legacyDb.transactionDao().insert(
            TransactionEntity(
                amountInCents = 4_500L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "groceries"
            )
        )
        val planId = legacyDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
                amountInCents = 5_000L, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 1
            )
        )
        legacyDb.categoryDao().insert(
            CategoryEntity(name = "Custom", normalizedName = "custom", type = TransactionType.EXPENSE, isBuiltIn = false)
        )
        legacyDb.budgetDao().insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 20_000L))
        legacyDb.close()

        // Reopen as the real (v7) database, applying only MIGRATION_6_7 - exactly as a real
        // device already at version 6 would upgrade.
        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals(4_500L, transactions[0].amountInCents)
        assertEquals("groceries", transactions[0].note)

        val plans = migratedDb.recurringPaymentPlanDao().observeAll().first()
        assertEquals(1, plans.size)
        assertEquals(planId, plans[0].id)

        val categories = migratedDb.categoryDao().observeByType(TransactionType.EXPENSE).first()
        assertTrue(categories.any { it.name == "Custom" })

        val budgets = migratedDb.budgetDao().observeAll().first()
        assertEquals(1, budgets.size)

        // The new group-expense tables exist and are fully usable through the real DAOs.
        val groupId = migratedDb.expenseGroupDao().insert(ExpenseGroupEntity(name = "Trip"))
        val annId = migratedDb.groupMemberDao().insert(GroupMemberEntity(groupId = groupId, name = "Ann"))
        val benId = migratedDb.groupMemberDao().insert(GroupMemberEntity(groupId = groupId, name = "Ben"))

        val expenseId = migratedDb.groupExpenseDao().insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 1_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 1_000L)
            )
        )
        val shares = migratedDb.groupExpenseDao().getSharesForExpense(expenseId)
        assertEquals(2, shares.size)
        assertEquals(2_000L, shares.sumOf { it.shareAmountCents })

        migratedDb.close()
    }
}
