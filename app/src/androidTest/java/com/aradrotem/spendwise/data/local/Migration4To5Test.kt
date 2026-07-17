package com.aradrotem.spendwise.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

// Mirrors the version-5 `transactions` table exactly - the schema already installed as "version
// 5" on physical test devices that ran the Step 10 build (after the migration fix), before
// isOccurrenceModified was added in MIGRATION_5_6. Must NOT gain that column: that would defeat
// the point of isolating the 4->5 verification from the 5->6 one (see Migration5To6Test).
// Not private: reused from Migration5To6Test.kt and MigrationChainTest.kt in this package.
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["recurringPlanId", "scheduledYearMonth"], unique = true)]
)
data class LegacyTransactionEntityV5(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountInCents: Long,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val note: String = "",
    val recurringPlanId: Long? = null,
    val installmentNumber: Int? = null,
    val totalInstallments: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val isAutomaticallyGenerated: Boolean = false,
    val scheduledYearMonth: String? = null,
    val sourceTitle: String? = null
)

@Dao
interface LegacyTransactionDaoV5 {
    @Insert
    suspend fun insert(transaction: LegacyTransactionEntityV5): Long

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<LegacyTransactionEntityV5>
}

// RecurringPaymentPlanEntity/Dao are real and unchanged since Step 10 first shipped, so they're
// reused as-is (only transactions changed shape across v4/v5/v6).
@Database(
    entities = [LegacyTransactionEntityV5::class, CategoryEntity::class, BudgetEntity::class, RecurringPaymentPlanEntity::class],
    version = 5,
    exportSchema = false
)
abstract class LegacyDatabaseV5 : RoomDatabase() {
    abstract fun legacyTransactionDao(): LegacyTransactionDaoV5
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringPaymentPlanDao(): RecurringPaymentPlanDao
}

// Uses LegacyDatabaseV4/LegacyTransactionEntityV4/LegacyTransactionDaoV4 from Migration3To4Test.kt
// (same package): that frozen shape *is* the real original Step 10 version-4 schema, i.e. what's
// already installed on a physical test device that ran the original build.
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private val dbName = "migration-4-5-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate4To5_preservesExistingDataAndAddsNullableSourceTitleColumn() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        // Build a realistic "before" (v4) database: a manual transaction, a generated Expense
        // transaction, a generated installment transaction, a recurring plan, a custom category,
        // and a budget - matching what a real upgrading device could plausibly already have.
        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV4::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV4(
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
            LegacyTransactionEntityV4(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 2_000L,
                recurringPlanId = monthlyPlanId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03"
            )
        )
        val installmentPlanId = legacyDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.INSTALLMENT, title = "Laptop", categoryName = "SHOPPING",
                totalAmountInCents = 90_000L, totalInstallments = 3, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 15
            )
        )
        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV4(
                amountInCents = 30_000L, type = TransactionType.EXPENSE, category = "SHOPPING", timestamp = 3_000L,
                recurringPlanId = installmentPlanId, installmentNumber = 1, totalInstallments = 3,
                isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
            )
        )
        legacyDb.categoryDao().insert(
            CategoryEntity(name = "Custom", normalizedName = "custom", type = TransactionType.EXPENSE, isBuiltIn = false)
        )
        legacyDb.budgetDao().insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 20_000L))
        legacyDb.close()

        // Reopen at version 5, applying only MIGRATION_4_5 - exactly as a real device already at
        // version 4 (from the original Step 10 build) would upgrade. Targets the frozen
        // LegacyDatabaseV5 shape (not the real, now-v6 SpendWiseDatabase) so this test verifies
        // MIGRATION_4_5 in isolation, matching the pattern established for 3->4.
        val migratedDb = Room.databaseBuilder(context, LegacyDatabaseV5::class.java, dbName)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.legacyTransactionDao().getAll().sortedBy { it.timestamp }
        assertEquals(3, transactions.size)

        val manual = transactions[0]
        assertEquals(4_500L, manual.amountInCents)
        assertEquals("FOOD", manual.category)
        assertEquals("groceries", manual.note)
        assertEquals(TransactionType.EXPENSE, manual.type)
        assertNull(manual.recurringPlanId)
        assertNull(manual.sourceTitle)

        val generatedExpense = transactions[1]
        assertEquals(5_000L, generatedExpense.amountInCents)
        assertEquals("HOUSING", generatedExpense.category)
        assertEquals(monthlyPlanId, generatedExpense.recurringPlanId)
        assertEquals("2026-03", generatedExpense.scheduledYearMonth)
        assertTrue(generatedExpense.isAutomaticallyGenerated)
        assertNull(generatedExpense.sourceTitle)

        val generatedInstallment = transactions[2]
        assertEquals(30_000L, generatedInstallment.amountInCents)
        assertEquals(installmentPlanId, generatedInstallment.recurringPlanId)
        assertEquals(1, generatedInstallment.installmentNumber)
        assertEquals(3, generatedInstallment.totalInstallments)
        assertNull(generatedInstallment.sourceTitle)

        val categories = migratedDb.categoryDao().observeByType(TransactionType.EXPENSE).first()
        assertTrue(categories.any { it.name == "Custom" })

        val budgets = migratedDb.budgetDao().observeAll().first()
        assertEquals(1, budgets.size)
        assertEquals(20_000L, budgets[0].monthlyLimitCents)

        val plans = migratedDb.recurringPaymentPlanDao().observeAll().first()
        assertEquals(2, plans.size)

        // A newly inserted row can set a non-null sourceTitle after migration (column exists and
        // is writable, even though nothing in the v4->v5 migration itself populates it).
        migratedDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV5(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 4_000L,
                recurringPlanId = monthlyPlanId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-04",
                sourceTitle = "Rent"
            )
        )
        val newTransaction = migratedDb.legacyTransactionDao().getAll().first { it.scheduledYearMonth == "2026-04" }
        assertEquals("Rent", newTransaction.sourceTitle)

        migratedDb.close()
    }
}
