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

// Version-12 schema is identical to the real (pre-completion-pass) entities - MIGRATION_12_13
// only adds the new group_expense_pending_deletions table - so this reuses the real entity/DAO
// classes directly, same convention as Migration11To12Test.
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
        ReceiptPendingDeletionEntity::class,
        NotifiedBudgetThresholdEntity::class,
        NotifiedRecurringReminderEntity::class,
        CachedExchangeRateEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class LegacyDatabaseV12 : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {

    private val dbName = "migration-12-13-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate12To13_preservesExistingDataAndAddsPendingDeletionTable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV12::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.transactionDao().insert(
            TransactionEntity(amountInCents = 1_500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L, note = "pre-existing")
        )
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals("pre-existing", transactions[0].note)

        val pendingDeletionDao = migratedDb.groupExpensePendingDeletionDao()
        pendingDeletionDao.insert(GroupExpensePendingDeletionEntity(groupSyncId = "group-1", cloudId = "expense-1"))
        assertEquals(1, pendingDeletionDao.getAll().size)

        migratedDb.close()
    }
}
