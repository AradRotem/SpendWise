package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetDao: BudgetDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        budgetDao = database.budgetDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun deleteCustomCategoryAndReassign_removesBudgetAndReassignsOnlyMatchingExpenseTransactions() = runBlocking {
        val customCategory = CategoryEntity(
            name = "GADGETS",
            normalizedName = "gadgets",
            type = TransactionType.EXPENSE,
            isBuiltIn = false
        )
        val categoryId = categoryDao.insert(customCategory)

        budgetDao.insert(BudgetEntity(categoryName = "GADGETS", monthlyLimitCents = 100_00))

        transactionDao.insert(
            TransactionEntity(amountInCents = 2000, type = TransactionType.EXPENSE, category = "GADGETS", timestamp = 1000L)
        )
        // An income transaction that happens to share the same category name/type-independent string,
        // used to confirm the type filter on reassignment is respected.
        transactionDao.insert(
            TransactionEntity(amountInCents = 3000, type = TransactionType.INCOME, category = "GADGETS", timestamp = 1000L)
        )
        transactionDao.insert(
            TransactionEntity(amountInCents = 500, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1000L)
        )

        categoryDao.deleteCustomCategoryAndReassign(customCategory.copy(id = categoryId))

        assertNull(budgetDao.findByCategory("GADGETS"))

        val allTransactions = transactionDao.observeAll().first()
        val expenseGadgets = allTransactions.count { it.type == TransactionType.EXPENSE && it.category == "GADGETS" }
        val expenseOther = allTransactions.count { it.type == TransactionType.EXPENSE && it.category == "OTHER" }
        val incomeGadgets = allTransactions.count { it.type == TransactionType.INCOME && it.category == "GADGETS" }
        val expenseFood = allTransactions.count { it.type == TransactionType.EXPENSE && it.category == "FOOD" }

        assertEquals(0, expenseGadgets)
        assertEquals(1, expenseOther)
        assertEquals(1, incomeGadgets)
        assertEquals(1, expenseFood)
    }
}
