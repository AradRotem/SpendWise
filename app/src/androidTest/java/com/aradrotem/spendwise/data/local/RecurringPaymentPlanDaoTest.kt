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
class RecurringPaymentPlanDaoTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var planDao: RecurringPaymentPlanDao
    private lateinit var transactionDao: TransactionDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        planDao = database.recurringPaymentPlanDao()
        transactionDao = database.transactionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun monthlyPlan(status: RecurringPlanStatus = RecurringPlanStatus.ACTIVE) = RecurringPaymentPlanEntity(
        type = RecurringPlanType.MONTHLY_RECURRING,
        title = "Rent",
        categoryName = "HOUSING",
        amountInCents = 5_000L,
        firstPaymentDateMillis = 1_000L,
        preferredDayOfMonth = 1,
        status = status
    )

    private fun installmentPlan() = RecurringPaymentPlanEntity(
        type = RecurringPlanType.INSTALLMENT,
        title = "Laptop",
        categoryName = "SHOPPING",
        totalAmountInCents = 100_000L,
        totalInstallments = 3,
        firstPaymentDateMillis = 1_000L,
        preferredDayOfMonth = 15
    )

    private fun salaryPlan() = RecurringPaymentPlanEntity(
        type = RecurringPlanType.MONTHLY_SALARY,
        title = "Monthly salary",
        categoryName = "SALARY",
        amountInCents = 1_000_000L,
        firstPaymentDateMillis = 1_000L,
        preferredDayOfMonth = 1
    )

    @Test
    fun insertAndReadMonthlyRecurringPlan() = runBlocking {
        val id = planDao.insert(monthlyPlan())

        val stored = planDao.getById(id)

        assertEquals(RecurringPlanType.MONTHLY_RECURRING, stored?.type)
        assertEquals("Rent", stored?.title)
        assertEquals(5_000L, stored?.amountInCents)
        assertEquals(null, stored?.totalAmountInCents)
        assertEquals(null, stored?.totalInstallments)
    }

    @Test
    fun insertAndReadInstallmentPlan() = runBlocking {
        val id = planDao.insert(installmentPlan())

        val stored = planDao.getById(id)

        assertEquals(RecurringPlanType.INSTALLMENT, stored?.type)
        assertEquals(100_000L, stored?.totalAmountInCents)
        assertEquals(3, stored?.totalInstallments)
        assertEquals(null, stored?.amountInCents)
    }

    @Test
    fun insertAndReadMonthlySalaryPlan() = runBlocking {
        val id = planDao.insert(salaryPlan())

        val stored = planDao.getById(id)

        assertEquals(RecurringPlanType.MONTHLY_SALARY, stored?.type)
        assertEquals("SALARY", stored?.categoryName)
        assertEquals(1_000_000L, stored?.amountInCents)
    }

    @Test
    fun pauseAndResumePlan() = runBlocking {
        val id = planDao.insert(monthlyPlan())

        planDao.updateStatus(id, RecurringPlanStatus.PAUSED)
        assertEquals(RecurringPlanStatus.PAUSED, planDao.getById(id)?.status)

        planDao.updateStatus(id, RecurringPlanStatus.ACTIVE)
        assertEquals(RecurringPlanStatus.ACTIVE, planDao.getById(id)?.status)
    }

    @Test
    fun getActivePlans_excludesInactivePlans() = runBlocking {
        val activeId = planDao.insert(monthlyPlan(status = RecurringPlanStatus.ACTIVE))
        planDao.insert(monthlyPlan(status = RecurringPlanStatus.PAUSED))
        planDao.insert(monthlyPlan(status = RecurringPlanStatus.STOPPED))
        planDao.insert(monthlyPlan(status = RecurringPlanStatus.COMPLETED))

        val activePlans = planDao.getActivePlans()

        assertEquals(1, activePlans.size)
        assertEquals(activeId, activePlans.single().id)
    }

    @Test
    fun generatedPaymentUniqueness_preventsDuplicatesForSamePlanAndMonth() = runBlocking {
        val planId = planDao.insert(monthlyPlan())
        val payment = TransactionEntity(
            amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 1_000L,
            recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03"
        )

        val firstInsert = transactionDao.insertGeneratedIgnoringConflicts(listOf(payment))
        val secondInsert = transactionDao.insertGeneratedIgnoringConflicts(listOf(payment))

        assertTrue(firstInsert.single() != -1L)
        assertEquals(-1L, secondInsert.single())
        assertEquals(1, transactionDao.observeByPlan(planId).first().size)
    }

    @Test
    fun historicalGeneratedTransactions_remainAfterStoppingPlan() = runBlocking {
        val planId = planDao.insert(monthlyPlan())
        transactionDao.insertGeneratedIgnoringConflicts(
            listOf(
                TransactionEntity(
                    amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 1_000L,
                    recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03"
                )
            )
        )

        planDao.updateStatus(planId, RecurringPlanStatus.STOPPED)

        assertEquals(RecurringPlanStatus.STOPPED, planDao.getById(planId)?.status)
        assertEquals(1, transactionDao.observeByPlan(planId).first().size)
    }

    @Test
    fun deletingPlan_removesPlanButPreservesGeneratedTransactions() = runBlocking {
        val plan = monthlyPlan()
        val planId = planDao.insert(plan)
        transactionDao.insertGeneratedIgnoringConflicts(
            listOf(
                TransactionEntity(
                    amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 1_000L,
                    recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03",
                    sourceTitle = "Rent"
                )
            )
        )

        // No FK from transactions to recurring_payment_plans (see RecurringPaymentPlanEntity),
        // so deleting the plan row must not cascade into the transactions it generated.
        planDao.delete(plan.copy(id = planId))

        assertEquals(null, planDao.getById(planId))
        val remaining = transactionDao.observeByPlan(planId).first()
        assertEquals(1, remaining.size)
        assertEquals("Rent", remaining.single().sourceTitle)
    }
}
