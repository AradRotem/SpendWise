package com.aradrotem.spendwise.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Every chart must have an accompanying textual representation (spec requirement), enforced here
// via Compose's contentDescription/onNodeWithText rather than relying on visual inspection alone.
@RunWith(AndroidJUnit4::class)
class AnalyticsChartsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun donutChart_emptyData_showsNoExpenseDataDescription() {
        composeRule.setContent { DonutChart(segments = emptyList()) }

        composeRule.onNodeWithContentDescription("No expense data").assertIsDisplayed()
    }

    @Test
    fun donutChart_withSegments_exposesCategoryAndAmountInDescription() {
        composeRule.setContent {
            DonutChart(
                segments = listOf(
                    DonutSegment("Food", 5_000L, 100f, Color.Blue)
                )
            )
        }

        composeRule.onNodeWithContentDescription("Food: 50.00, 100 percent").assertIsDisplayed()
    }

    @Test
    fun incomeExpenseBarChart_showsMonthLabelsAndLegend() {
        composeRule.setContent {
            IncomeExpenseBarChart(
                months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
                incomeCents = listOf(10_000L, 12_000L),
                expenseCents = listOf(5_000L, 6_000L)
            )
        }

        composeRule.onNodeWithText("Jan 26").assertIsDisplayed()
        composeRule.onNodeWithText("Feb 26").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
        composeRule.onNodeWithText("Expenses").assertIsDisplayed()
    }

    @Test
    fun incomeExpenseBarChart_exposesExactValuesAsContentDescriptions() {
        composeRule.setContent {
            IncomeExpenseBarChart(
                months = listOf(YearMonth.of(2026, 1)),
                incomeCents = listOf(10_000L),
                expenseCents = listOf(5_000L)
            )
        }

        composeRule.onNodeWithContentDescription("Jan 26 income 100.00").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Jan 26 expenses 50.00").assertIsDisplayed()
    }

    @Test
    fun singleSeriesBarChart_zeroValue_stillExposesExactValue() {
        composeRule.setContent {
            SingleSeriesBarChart(
                months = listOf(YearMonth.of(2026, 1)),
                valuesCents = listOf(0L),
                barColor = Color.Red
            )
        }

        composeRule.onNodeWithContentDescription("Jan 26: 0.00").assertIsDisplayed()
        composeRule.onNodeWithText("Jan 26").assertIsDisplayed()
    }
}
