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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Version-8 schema is identical to the real (pre-Step-18) entities - MIGRATION_8_9 only adds
// nullable/defaulted receipt columns to transactions plus the new receipt_pending_deletions
// table - so this reuses the real entity/DAO classes directly, same convention as
// Migration7To8Test.
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
        LegacyImportRecordEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class LegacyDatabaseV8 : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

@RunWith(AndroidJUnit4::class)
class Migration8To9Test {

    private val dbName = "migration-8-9-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate8To9_preservesExistingTransactionsAndDefaultsNoReceipt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV8::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.transactionDao().insert(
            TransactionEntity(
                amountInCents = 3_000L, type = TransactionType.EXPENSE, category = "SHOPPING",
                timestamp = 1_000L, note = "pre-existing"
            )
        )
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals("pre-existing", transactions[0].note)
        // Pre-existing transaction defaults safely to "no receipt".
        assertNull(transactions[0].receiptId)
        assertNull(transactions[0].receiptStoragePath)
        assertFalse(transactions[0].receiptUploadPending)

        // The new receipt_pending_deletions table exists and is fully usable through the real DAO.
        migratedDb.receiptPendingDeletionDao().insert(
            ReceiptPendingDeletionEntity(storagePath = "users/uid/receipts/r1/photo.jpg")
        )
        val pendingDeletions = migratedDb.receiptPendingDeletionDao().getAll()
        assertEquals(1, pendingDeletions.size)

        migratedDb.close()
    }
}
