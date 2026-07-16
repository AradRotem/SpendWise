package com.aradrotem.spendwise.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aradrotem.spendwise.data.local.RecurringPlanType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Proves the Monthly Salary fix is real in the actual rendered Compose UI, not only at the
// enum/domain level: all three plan-type options must be physically present and tappable. Uses
// only the selector composable in isolation (no Application/database dependency), so it's safe
// to run without touching the real app database.
@RunWith(AndroidJUnit4::class)
class RecurringPlanTypeSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allThreePlanTypeOptionsAreDisplayed() {
        composeRule.setContent {
            RecurringPlanTypeSelector(selectedType = RecurringPlanType.MONTHLY_RECURRING, onTypeSelected = {})
        }

        composeRule.onNodeWithText("Monthly expense").assertIsDisplayed()
        composeRule.onNodeWithText("Installment purchase").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly salary").assertIsDisplayed()
    }

    @Test
    fun clickingMonthlySalary_invokesCallbackWithMonthlySalary() {
        var selected: RecurringPlanType? = null
        composeRule.setContent {
            RecurringPlanTypeSelector(
                selectedType = RecurringPlanType.MONTHLY_RECURRING,
                onTypeSelected = { selected = it }
            )
        }

        composeRule.onNodeWithText("Monthly salary").performClick()

        assertEquals(RecurringPlanType.MONTHLY_SALARY, selected)
    }

    @Test
    fun clickingInstallmentPurchase_invokesCallbackWithInstallment() {
        var selected: RecurringPlanType? = null
        composeRule.setContent {
            RecurringPlanTypeSelector(
                selectedType = RecurringPlanType.MONTHLY_SALARY,
                onTypeSelected = { selected = it }
            )
        }

        composeRule.onNodeWithText("Installment purchase").performClick()

        assertEquals(RecurringPlanType.INSTALLMENT, selected)
    }
}
