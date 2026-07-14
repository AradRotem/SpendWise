package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetDaoTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var dao: BudgetDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.budgetDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserve_returnsInsertedBudget() = runBlocking {
        dao.insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 50_00))

        val result = dao.observeAll().first()

        assertEquals(1, result.size)
        assertEquals("FOOD", result[0].categoryName)
        assertEquals(50_00L, result[0].monthlyLimitCents)
    }

    @Test
    fun onlyOneBudgetPerCategory_isEnforcedByUniqueIndex() = runBlocking {
        dao.insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 50_00))

        var threw = false
        try {
            dao.insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 75_00))
        } catch (e: Exception) {
            threw = true
        }

        assertTrue(threw)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun update_changesLimitWithoutCreatingDuplicate() = runBlocking {
        val id = dao.insert(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 50_00))

        dao.update(BudgetEntity(id = id, categoryName = "FOOD", monthlyLimitCents = 80_00))

        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals(80_00L, result[0].monthlyLimitCents)
    }

    @Test
    fun delete_removesOnlyTheBudget() = runBlocking {
        val transactionDao = database.transactionDao()
        transactionDao.insert(
            TransactionEntity(amountInCents = 1000, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1000L)
        )
        val budget = BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 50_00)
        val id = dao.insert(budget)

        dao.delete(budget.copy(id = id))

        assertTrue(dao.observeAll().first().isEmpty())
        assertEquals(1, transactionDao.observeAll().first().size)
    }

    @Test
    fun findByCategory_returnsNullWhenAbsent() = runBlocking {
        assertNull(dao.findByCategory("FOOD"))
    }
}
