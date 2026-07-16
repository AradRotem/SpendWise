package com.aradrotem.spendwise.ui.screens

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddRecurringPlanUiStateTest {

    private fun millisForDay(day: Int): Long =
        LocalDate.of(2026, 3, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun preferredDayWarning_hiddenForDays1To28() {
        for (day in 1..28) {
            val state = AddRecurringPlanUiState(preferredDayOfMonthText = day.toString())
            assertFalse("day $day should not warn", state.showPreferredDayWarning)
        }
    }

    @Test
    fun preferredDayWarning_shownForDays29To31() {
        for (day in 29..31) {
            val state = AddRecurringPlanUiState(preferredDayOfMonthText = day.toString())
            assertTrue("day $day should warn", state.showPreferredDayWarning)
        }
    }

    @Test
    fun preferredDayWarning_hiddenWhenTextIsNotAValidNumber() {
        val state = AddRecurringPlanUiState(preferredDayOfMonthText = "")
        assertFalse(state.showPreferredDayWarning)
    }

    @Test
    fun firstPaymentDayWarning_hiddenForDays1To28() {
        for (day in 1..28) {
            val state = AddRecurringPlanUiState(firstPaymentDateMillis = millisForDay(day))
            assertFalse("day $day should not warn", state.showFirstPaymentDayWarning)
        }
    }

    @Test
    fun firstPaymentDayWarning_shownForDays29To31() {
        for (day in 29..31) {
            val state = AddRecurringPlanUiState(firstPaymentDateMillis = millisForDay(day))
            assertTrue("day $day should warn", state.showFirstPaymentDayWarning)
        }
    }
}
