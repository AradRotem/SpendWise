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

// Mirrors the pre-Step-10 (v3) `transactions` table exactly, so LegacyDatabaseV3 below produces
// an accurate "before" schema for the migration tests without needing exported Room schema JSON
// (schema export is not enabled in this project - see SpendWiseDatabase's exportSchema = false).
// Must NOT be changed alongside TransactionEntity: it is a frozen historical snapshot.
// Not private: reused from Migration4To5Test.kt and MigrationChainTest.kt in this package.
@Entity(tableName = "transactions")
data class LegacyTransactionEntityV3(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountInCents: Long,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val note: String = ""
)

@Dao
interface LegacyTransactionDaoV3 {
    @Insert
    suspend fun insert(transaction: LegacyTransactionEntityV3): Long
}

// CategoryEntity and BudgetEntity are untouched by Step 10, so they're reused as-is here.
@Database(
    entities = [LegacyTransactionEntityV3::class, CategoryEntity::class, BudgetEntity::class],
    version = 3,
    exportSchema = false
)
abstract class LegacyDatabaseV3 : RoomDatabase() {
    abstract fun legacyTransactionDao(): LegacyTransactionDaoV3
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
}

// Mirrors the *original* Step 10 (v4) `transactions` table exactly - the schema already installed
// as "version 4" on physical test devices that ran the original Step 10 build, before sourceTitle
// was added in MIGRATION_4_5. Must NOT gain a sourceTitle field: that would defeat the point of
// isolating the 3->4 verification from the 4->5 one. RecurringPaymentPlanEntity/Dao are real and
// unchanged since Step 10 first shipped, so they're reused as-is (only transactions gained a
// column later).
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["recurringPlanId", "scheduledYearMonth"], unique = true)]
)
data class LegacyTransactionEntityV4(
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
    val scheduledYearMonth: String? = null
)

@Dao
interface LegacyTransactionDaoV4 {
    @Insert
    suspend fun insert(transaction: LegacyTransactionEntityV4): Long

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<LegacyTransactionEntityV4>
}

@Database(
    entities = [LegacyTransactionEntityV4::class, CategoryEntity::class, BudgetEntity::class, RecurringPaymentPlanEntity::class],
    version = 4,
    exportSchema = false
)
abstract class LegacyDatabaseV4 : RoomDatabase() {
    abstract fun legacyTransactionDao(): LegacyTransactionDaoV4
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringPaymentPlanDao(): RecurringPaymentPlanDao
}

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val dbName = "migration-3-4-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate3To4_preservesExistingDataAndProducesOriginalVersion4Schema() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        // Build the "before" (v3) database using real v1-3 entity shapes, so its on-disk schema
        // is exactly what Room would have generated pre-Step-10 - not a hand-guessed DDL string.
        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV3::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV3(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "groceries"
            )
        )
        legacyDb.categoryDao().insert(
            CategoryEntity(name = "Custom", normalizedName = "custom", type = TransactionType.EXPENSE, isBuiltIn = false)
        )
        legacyDb.budgetDao().insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 20_000L))
        legacyDb.close()

        // Reopen as the frozen v4 shape (LegacyDatabaseV4, not today's v5 SpendWiseDatabase) with
        // ONLY MIGRATION_3_4, so this test genuinely isolates the 3->4 step: Room validates the
        // result against the *original* version-4 schema, exactly what's on a physical device
        // that ran the original Step 10 build, not against anything added since.
        val migratedDb = Room.databaseBuilder(context, LegacyDatabaseV4::class.java, dbName)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.legacyTransactionDao().getAll()
        assertEquals(1, transactions.size)
        assertEquals(5_000L, transactions[0].amountInCents)
        assertEquals("groceries", transactions[0].note)
        assertNull(transactions[0].recurringPlanId)
        assertNull(transactions[0].scheduledYearMonth)
        assertEquals(false, transactions[0].isAutomaticallyGenerated)

        val categories = migratedDb.categoryDao().observeByType(TransactionType.EXPENSE).first()
        assertTrue(categories.any { it.name == "Custom" })

        val budgets = migratedDb.budgetDao().observeAll().first()
        assertEquals(1, budgets.size)
        assertEquals(20_000L, budgets[0].monthlyLimitCents)

        // Recurring-transaction plan table exists and is queryable through the real DAO.
        assertTrue(migratedDb.recurringPaymentPlanDao().observeAll().first().isEmpty())
        val planId = migratedDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
                amountInCents = 5_000L, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 1
            )
        )
        assertEquals(1, migratedDb.recurringPaymentPlanDao().observeAll().first().size)

        // The original Step 10 transaction-link columns exist and round-trip.
        val generatedId = migratedDb.legacyTransactionDao().insert(
            LegacyTransactionEntityV4(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 2_000L,
                recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03"
            )
        )
        assertTrue(generatedId > 0)

        migratedDb.close()
    }

    @Test
    fun migrate3To4_uniqueIndexOnRecurringPlanIdAndScheduledYearMonthExists() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        Room.databaseBuilder(context, LegacyDatabaseV3::class.java, dbName)
            .allowMainThreadQueries()
            .build()
            .close()

        val migratedDb = Room.databaseBuilder(context, LegacyDatabaseV4::class.java, dbName)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val planId = migratedDb.recurringPaymentPlanDao().insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
                amountInCents = 5_000L, firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 1
            )
        )
        val payment = LegacyTransactionEntityV4(
            amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 1_000L,
            recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03"
        )

        migratedDb.legacyTransactionDao().insert(payment)
        var threw = false
        try {
            migratedDb.legacyTransactionDao().insert(payment)
        } catch (e: Exception) {
            threw = true
        }

        assertTrue(threw)
        assertEquals(1, migratedDb.legacyTransactionDao().getAll().size)

        migratedDb.close()
    }
}
