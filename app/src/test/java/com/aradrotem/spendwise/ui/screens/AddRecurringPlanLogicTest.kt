package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure-logic tests for the Add/Edit Recurring Transaction screen's type <-> category rules.
// These functions are kept as top-level functions (not ViewModel members) specifically so they
// can be tested without instantiating a real ViewModel, which needs a Main dispatcher not
// available in this project's plain JUnit setup (no kotlinx-coroutines-test dependency).
class AddRecurringPlanLogicTest {

    @Test
    fun allThreeRecurringPlanTypesAreRepresented() {
        assertEquals(
            setOf(RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.INSTALLMENT, RecurringPlanType.MONTHLY_SALARY),
            RecurringPlanType.entries.toSet()
        )
    }

    @Test
    fun categoryTypeForPlanType_monthlySalaryUsesIncome() {
        assertEquals(TransactionType.INCOME, categoryTypeForPlanType(RecurringPlanType.MONTHLY_SALARY))
    }

    @Test
    fun categoryTypeForPlanType_monthlyExpenseAndInstallmentUseExpense() {
        assertEquals(TransactionType.EXPENSE, categoryTypeForPlanType(RecurringPlanType.MONTHLY_RECURRING))
        assertEquals(TransactionType.EXPENSE, categoryTypeForPlanType(RecurringPlanType.INSTALLMENT))
    }

    @Test
    fun resetCategoryIfInvalid_keepsCategoryStillValidForNewType() {
        val result = resetCategoryIfInvalid("SALARY", listOf("SALARY", "OTHER"))
        assertEquals("SALARY", result)
    }

    @Test
    fun resetCategoryIfInvalid_switchingAwayFromSalaryResetsInvalidIncomeCategory() {
        // e.g. "SALARY" was selected while type was Monthly salary; switching to Monthly expense
        // means only Expense categories are valid, so the Income-only selection must be cleared.
        val result = resetCategoryIfInvalid("SALARY", listOf("FOOD", "HOUSING", "OTHER"))
        assertNull(result)
    }

    @Test
    fun resetCategoryIfInvalid_nullCategoryStaysNull() {
        val result = resetCategoryIfInvalid(null, listOf("FOOD"))
        assertNull(result)
    }
}
