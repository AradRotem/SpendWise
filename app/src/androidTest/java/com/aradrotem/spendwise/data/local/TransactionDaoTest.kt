package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.transactionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserveAll_returnsNewestFirst() = runBlocking {
        val older = TransactionEntity(
            amountInCents = 500,
            type = TransactionType.EXPENSE,
            category = "FOOD",
            timestamp = 1000L
        )
        val newer = TransactionEntity(
            amountInCents = 1200,
            type = TransactionType.INCOME,
            category = "SALARY",
            timestamp = 2000L
        )

        dao.insert(older)
        dao.insert(newer)

        val result = dao.observeAll().first()

        assertEquals(2, result.size)
        assertEquals(newer.timestamp, result[0].timestamp)
        assertEquals(older.timestamp, result[1].timestamp)
    }

    @Test
    fun observeExpenseTotalsByCategory_groupsExpensesInRangeByCategory() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 1000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1000L))
        dao.insert(TransactionEntity(amountInCents = 500, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 2000L))
        dao.insert(TransactionEntity(amountInCents = 300, type = TransactionType.EXPENSE, category = "TRANSPORT", timestamp = 1500L))

        val totals = dao.observeExpenseTotalsByCategory(0L, 3000L).first()
        val byCategory = totals.associateBy({ it.category }, { it.totalCents })

        assertEquals(1500L, byCategory["FOOD"])
        assertEquals(300L, byCategory["TRANSPORT"])
    }

    @Test
    fun observeExpenseTotalsByCategory_excludesIncomeTransactions() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 1000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1000L))
        dao.insert(TransactionEntity(amountInCents = 5000, type = TransactionType.INCOME, category = "SALARY", timestamp = 1000L))

        val totals = dao.observeExpenseTotalsByCategory(0L, 3000L).first()

        assertEquals(1, totals.size)
        assertEquals("FOOD", totals[0].category)
        assertEquals(1000L, totals[0].totalCents)
    }

    @Test
    fun observeExpenseTotalsByCategory_excludesTransactionsOutsideRange() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 1000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 500L))
        dao.insert(TransactionEntity(amountInCents = 2000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 5000L))

        val totals = dao.observeExpenseTotalsByCategory(1000L, 3000L).first()

        assertTrue(totals.isEmpty())
    }

    @Test
    fun observeExpenseTotalsByCategory_sortsHighestToLowest() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 500, type = TransactionType.EXPENSE, category = "TRANSPORT", timestamp = 1000L))
        dao.insert(TransactionEntity(amountInCents = 3000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1000L))
        dao.insert(TransactionEntity(amountInCents = 1500, type = TransactionType.EXPENSE, category = "RENT", timestamp = 1000L))

        val totals = dao.observeExpenseTotalsByCategory(0L, 3000L).first()

        assertEquals(listOf("FOOD", "RENT", "TRANSPORT"), totals.map { it.category })
    }

    @Test
    fun observeTotalByType_sumsCurrentMonthIncomeAndExpenseSeparately() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 10_000, type = TransactionType.INCOME, category = "SALARY", timestamp = 1_500L))
        dao.insert(TransactionEntity(amountInCents = 2_000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_800L))
        dao.insert(TransactionEntity(amountInCents = 500, type = TransactionType.EXPENSE, category = "TRANSPORT", timestamp = 2_200L))

        val income = dao.observeTotalByType(TransactionType.INCOME, 1_000L, 3_000L).first()
        val expense = dao.observeTotalByType(TransactionType.EXPENSE, 1_000L, 3_000L).first()

        assertEquals(10_000L, income)
        assertEquals(2_500L, expense)
    }

    @Test
    fun observeTotalByType_excludesPreviousAndNextMonthTransactions() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 1_000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 500L)) // previous month
        dao.insert(TransactionEntity(amountInCents = 2_000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_500L)) // current month
        dao.insert(TransactionEntity(amountInCents = 4_000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 3_000L)) // next month (range end is exclusive)

        val expense = dao.observeTotalByType(TransactionType.EXPENSE, 1_000L, 3_000L).first()

        assertEquals(2_000L, expense)
    }

    @Test
    fun observeTotalByType_returnsZeroWhenNoMatchingTransactions() = runBlocking {
        val income = dao.observeTotalByType(TransactionType.INCOME, 0L, 1_000L).first()

        assertEquals(0L, income)
    }

    @Test
    fun observeRecent_returnsNewestFirstLimitedToFive() = runBlocking {
        for (i in 1..7) {
            dao.insert(
                TransactionEntity(
                    amountInCents = 100L * i,
                    type = if (i % 2 == 0) TransactionType.INCOME else TransactionType.EXPENSE,
                    category = "FOOD",
                    timestamp = i * 1000L
                )
            )
        }

        val recent = dao.observeRecent(5).first()

        assertEquals(5, recent.size)
        assertEquals(listOf(7000L, 6000L, 5000L, 4000L, 3000L), recent.map { it.timestamp })
    }

    @Test
    fun observeRecent_includesBothIncomeAndExpenseTransactions() = runBlocking {
        dao.insert(TransactionEntity(amountInCents = 1000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1000L))
        dao.insert(TransactionEntity(amountInCents = 2000, type = TransactionType.INCOME, category = "SALARY", timestamp = 2000L))

        val recent = dao.observeRecent(5).first()

        assertEquals(2, recent.size)
        assertTrue(recent.any { it.type == TransactionType.EXPENSE })
        assertTrue(recent.any { it.type == TransactionType.INCOME })
    }
}
