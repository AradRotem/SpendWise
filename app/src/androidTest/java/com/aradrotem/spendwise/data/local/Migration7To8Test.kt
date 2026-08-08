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
import org.junit.Test
import org.junit.runner.RunWith

// Version-7 schema is identical to the real (pre-Step-17) entities - MIGRATION_7_8 only adds the
// new sync_metadata/legacy_import_map tables - so this reuses the real entity/DAO classes
// directly, same convention as Migration6To7Test.
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
        GroupExpenseShareEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class LegacyDatabaseV7 : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
}

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val dbName = "migration-7-8-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate7To8_preservesExistingDataAndAddsSyncTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV7::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.transactionDao().insert(
            TransactionEntity(
                amountInCents = 1_200L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "lunch"
            )
        )
        legacyDb.budgetDao().insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 10_000L))
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals("lunch", transactions[0].note)

        val budgets = migratedDb.budgetDao().observeAll().first()
        assertEquals(1, budgets.size)

        // New sync tables exist and are fully usable through the real DAOs.
        migratedDb.syncMetadataDao().upsert(
            com.aradrotem.spendwise.data.sync.SyncMetadataEntity(
                entityType = "transaction", localId = transactions[0].id,
                syncId = "abc-123", updatedAt = 5_000L
            )
        )
        val pending = migratedDb.syncMetadataDao().getPendingByType("transaction")
        assertEquals(1, pending.size)
        assertEquals("abc-123", pending[0].syncId)

        migratedDb.legacyImportRecordDao().insert(
            com.aradrotem.spendwise.data.sync.LegacyImportRecordEntity(
                entityType = "transaction", legacyLocalId = transactions[0].id, newLocalId = 99L
            )
        )
        val record = migratedDb.legacyImportRecordDao().find("transaction", transactions[0].id)
        assertEquals(99L, record?.newLocalId)

        migratedDb.close()
    }
}
