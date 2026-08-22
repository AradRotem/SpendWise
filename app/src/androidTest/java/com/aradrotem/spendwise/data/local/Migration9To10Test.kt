package com.aradrotem.spendwise.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aradrotem.spendwise.data.sync.LegacyImportRecordEntity
import com.aradrotem.spendwise.data.sync.SyncMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Version-9 schema is identical to the real (pre-Step-19) entities - MIGRATION_9_10 only adds the
// new notified_budget_thresholds/notified_recurring_reminders dedup tables - so this reuses the
// real entity/DAO classes directly, same convention as Migration8To9Test.
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringPaymentPlanEntity::class,
        RecurringOccurrenceExceptionEntity::class,
        ExpenseGroupEntity::class,
        GroupMemberEntity::class,
        GroupExpenseEntity::class,
        GroupExpenseShareEntity::class,
        SyncMetadataEntity::class,
        LegacyImportRecordEntity::class,
        ReceiptPendingDeletionEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class LegacyDatabaseV9 : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    private val dbName = "migration-9-10-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate9To10_preservesExistingDataAndAddsNotificationStateTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV9::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.transactionDao().insert(
            TransactionEntity(
                amountInCents = 4_500L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "pre-existing"
            )
        )
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals("pre-existing", transactions[0].note)

        // The new notification-state tables exist and are fully usable through the real DAO,
        // including their unique indices.
        val notificationDao = migratedDb.notificationStateDao()
        notificationDao.insertBudgetThresholdNotified(
            NotifiedBudgetThresholdEntity(categoryName = "FOOD", yearMonth = "2026-08", thresholdType = "NEAR_BUDGET", notifiedAtEpochMillis = 1_000L)
        )
        assertEquals(1, notificationDao.getNotifiedBudgetThresholdKeys("2026-08").size)

        notificationDao.insertRecurringReminderNotified(
            NotifiedRecurringReminderEntity(planId = 1L, scheduledYearMonth = "2026-08", notifiedAtEpochMillis = 1_000L)
        )
        assertEquals(1, notificationDao.getNotifiedRecurringReminderKeys().size)

        migratedDb.close()
    }
}
