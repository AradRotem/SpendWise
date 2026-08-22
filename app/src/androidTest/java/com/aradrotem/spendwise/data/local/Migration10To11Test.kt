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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Version-10 schema is identical to the real (pre-currency) entities - MIGRATION_10_11 only adds
// the four nullable original-currency columns to transactions plus the new
// cached_exchange_rates table - so this reuses the real entity/DAO classes directly, same
// convention as Migration9To10Test.
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
        NotifiedRecurringReminderEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class LegacyDatabaseV10 : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val dbName = "migration-10-11-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate10To11_preservesExistingTransactionsAndDefaultsNoForeignCurrency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV10::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        legacyDb.transactionDao().insert(
            TransactionEntity(
                amountInCents = 2_000L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = 1_000L, note = "pre-existing"
            )
        )
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()

        val transactions = migratedDb.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        assertEquals("pre-existing", transactions[0].note)
        // Pre-existing transaction defaults safely to "not a foreign-currency entry".
        assertNull(transactions[0].originalAmountCents)
        assertNull(transactions[0].originalCurrencyCode)
        assertNull(transactions[0].conversionRate)

        // The new exchange-rate cache table exists and is fully usable through the real DAO.
        val rateDao = migratedDb.cachedExchangeRateDao()
        rateDao.upsert(CachedExchangeRateEntity(fromCurrency = "USD", toCurrency = "ILS", rate = 3.7, fetchedAtEpochMillis = 1_000L))
        val cached = rateDao.get("USD", "ILS")
        assertEquals(3.7, cached?.rate)

        migratedDb.close()
    }
}
