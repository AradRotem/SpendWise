package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPaymentScheduleTest {

    private val zoneId: ZoneId = ZoneOffset.UTC

    private fun millisFor(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Test
    fun safeDayOfMonth_clampsDay31ToFebruarysLastDay() {
        val result = RecurringPaymentSchedule.safeDayOfMonth(YearMonth.of(2026, 2), 31)
        assertEquals(LocalDate.of(2026, 2, 28), result)
    }

    @Test
    fun safeDayOfMonth_usesLeapYearFebruary29() {
        val result = RecurringPaymentSchedule.safeDayOfMonth(YearMonth.of(2028, 2), 31)
        assertEquals(LocalDate.of(2028, 2, 29), result)
    }

    @Test
    fun safeDayOfMonth_keepsExactDayWhenValid() {
        val result = RecurringPaymentSchedule.safeDayOfMonth(YearMonth.of(2026, 1), 31)
        assertEquals(LocalDate.of(2026, 1, 31), result)
    }

    @Test
    fun splitInstallments_distributesRemainderToFinalInstallment() {
        val result = RecurringPaymentSchedule.splitInstallments(100_000L, 3)
        assertEquals(listOf(33_333L, 33_333L, 33_334L), result)
        assertEquals(100_000L, result.sum())
    }

    @Test
    fun splitInstallments_evenSplitHasNoRemainder() {
        val result = RecurringPaymentSchedule.splitInstallments(90_000L, 3)
        assertEquals(listOf(30_000L, 30_000L, 30_000L), result)
    }

    @Test
    fun duePayments_monthlyPlan_returnsOnePaymentWhenDue() {
        val plan = monthlyPlan(startDate = LocalDate.of(2026, 3, 1), preferredDay = 1)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 3, 15), zoneId = zoneId)
        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 3, 1), result[0].dueDate)
        assertEquals("2026-03", result[0].scheduledYearMonth)
        assertNull(result[0].installmentNumber)
    }

    @Test
    fun duePayments_monthlyPlan_futurePaymentNotYetDue() {
        val plan = monthlyPlan(startDate = LocalDate.of(2026, 3, 1), preferredDay = 20)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 3, 15), zoneId = zoneId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun duePayments_monthlyPlan_catchesUpMissedMonths() {
        val plan = monthlyPlan(startDate = LocalDate.of(2026, 1, 5), preferredDay = 5)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 4, 10), zoneId = zoneId)
        assertEquals(listOf("2026-01", "2026-02", "2026-03", "2026-04"), result.map { it.scheduledYearMonth })
    }

    @Test
    fun duePayments_monthlyPlan_dayThirtyOneClampsInFebruary() {
        val plan = monthlyPlan(startDate = LocalDate.of(2026, 1, 31), preferredDay = 31)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 3, 1), zoneId = zoneId)
        val dates = result.map { it.dueDate }
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)), dates)
    }

    @Test
    fun duePayments_monthlyPlan_excludesPaymentsAfterEndDate() {
        val plan = monthlyPlan(
            startDate = LocalDate.of(2026, 1, 1),
            preferredDay = 1,
            endDate = LocalDate.of(2026, 2, 1)
        )
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 5, 1), zoneId = zoneId)
        assertEquals(listOf("2026-01", "2026-02"), result.map { it.scheduledYearMonth })
    }

    @Test
    fun duePayments_monthlyPlan_futureStartDateProducesNoPayments() {
        val plan = monthlyPlan(startDate = LocalDate.of(2026, 6, 1), preferredDay = 1)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 3, 1), zoneId = zoneId)
        assertTrue(result.isEmpty())
    }

    @Test
    fun duePayments_installmentPlan_generatesExactCountAndSum() {
        val plan = installmentPlan(firstPaymentDate = LocalDate.of(2026, 1, 15), total = 100_000L, count = 3)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 12, 1), zoneId = zoneId)
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result.map { it.installmentNumber })
        assertEquals(100_000L, result.sumOf { it.amountCents })
        assertEquals(33_334L, result.last().amountCents)
    }

    @Test
    fun duePayments_installmentPlan_stopsGeneratingAfterFinalInstallment() {
        val plan = installmentPlan(firstPaymentDate = LocalDate.of(2026, 1, 15), total = 30_000L, count = 3)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2030, 1, 1), zoneId = zoneId)
        assertEquals(3, result.size)
    }

    @Test
    fun duePayments_installmentPlan_respectsSafeDayOfMonth() {
        val plan = installmentPlan(firstPaymentDate = LocalDate.of(2026, 1, 31), total = 30_000L, count = 3)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 3, 31), zoneId = zoneId)
        val dates = result.map { it.dueDate }
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)), dates)
    }

    @Test
    fun nextDueDate_returnsFirstUpcomingDate() {
        val plan = monthlyPlan(startDate = LocalDate.of(2026, 1, 1), preferredDay = 1)
        val next = RecurringPaymentSchedule.nextDueDate(plan, today = LocalDate.of(2026, 3, 15), zoneId = zoneId)
        assertEquals(LocalDate.of(2026, 4, 1), next)
    }

    @Test
    fun nextDueDate_returnsNullWhenInstallmentPlanExhausted() {
        val plan = installmentPlan(firstPaymentDate = LocalDate.of(2026, 1, 1), total = 10_000L, count = 2)
        val next = RecurringPaymentSchedule.nextDueDate(plan, today = LocalDate.of(2030, 1, 1), zoneId = zoneId)
        assertNull(next)
    }

    @Test
    fun duePayments_monthlySalaryPlan_followsSameScheduleAsMonthlyExpense() {
        val plan = salaryPlan(startDate = LocalDate.of(2026, 1, 5), preferredDay = 5)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 4, 10), zoneId = zoneId)
        assertEquals(listOf("2026-01", "2026-02", "2026-03", "2026-04"), result.map { it.scheduledYearMonth })
    }

    @Test
    fun duePayments_monthlySalaryPlan_dayThirtyOneClampsInFebruary() {
        val plan = salaryPlan(startDate = LocalDate.of(2026, 1, 31), preferredDay = 31)
        val result = RecurringPaymentSchedule.duePayments(plan, today = LocalDate.of(2026, 3, 1), zoneId = zoneId)
        val dates = result.map { it.dueDate }
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)), dates)
    }

    private fun monthlyPlan(
        startDate: LocalDate,
        preferredDay: Int,
        endDate: LocalDate? = null
    ) = RecurringPaymentPlanEntity(
        id = 1L,
        type = RecurringPlanType.MONTHLY_RECURRING,
        title = "Rent",
        categoryName = "HOUSING",
        amountInCents = 1_000L,
        firstPaymentDateMillis = millisFor(startDate),
        preferredDayOfMonth = preferredDay,
        endDateMillis = endDate?.let(::millisFor),
        status = RecurringPlanStatus.ACTIVE
    )

    private fun installmentPlan(
        firstPaymentDate: LocalDate,
        total: Long,
        count: Int
    ) = RecurringPaymentPlanEntity(
        id = 2L,
        type = RecurringPlanType.INSTALLMENT,
        title = "Laptop",
        categoryName = "SHOPPING",
        totalAmountInCents = total,
        totalInstallments = count,
        firstPaymentDateMillis = millisFor(firstPaymentDate),
        preferredDayOfMonth = firstPaymentDate.dayOfMonth,
        status = RecurringPlanStatus.ACTIVE
    )

    private fun salaryPlan(
        startDate: LocalDate,
        preferredDay: Int,
        endDate: LocalDate? = null
    ) = RecurringPaymentPlanEntity(
        id = 3L,
        type = RecurringPlanType.MONTHLY_SALARY,
        title = "Monthly salary",
        categoryName = "SALARY",
        amountInCents = 10_000_00L,
        firstPaymentDateMillis = millisFor(startDate),
        preferredDayOfMonth = preferredDay,
        endDateMillis = endDate?.let(::millisFor),
        status = RecurringPlanStatus.ACTIVE
    )
}
