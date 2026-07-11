package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
            category = TransactionCategory.FOOD,
            timestamp = 1000L
        )
        val newer = TransactionEntity(
            amountInCents = 1200,
            type = TransactionType.INCOME,
            category = TransactionCategory.SALARY,
            timestamp = 2000L
        )

        dao.insert(older)
        dao.insert(newer)

        val result = dao.observeAll().first()

        assertEquals(2, result.size)
        assertEquals(newer.timestamp, result[0].timestamp)
        assertEquals(older.timestamp, result[1].timestamp)
    }
}
