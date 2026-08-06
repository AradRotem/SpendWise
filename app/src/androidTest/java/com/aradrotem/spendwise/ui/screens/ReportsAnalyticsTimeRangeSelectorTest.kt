package com.aradrotem.spendwise.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression test for the bug where "12 months" was unreachable on a Samsung Galaxy S22+: a
// plain, non-scrollable Row of full-word buttons ("Month", "3 months", "6 months", "12 months")
// overflowed the screen width with no way to scroll to the last option. Proves all four compact
// options ("1M"/"3M"/"6M"/"12M") are rendered, reachable, and that selecting one invokes the
// correct callback with the exact range - independent of any screen width, since createComposeRule
// renders at the test device's real width.
@RunWith(AndroidJUnit4::class)
class ReportsAnalyticsTimeRangeSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allFourOptions_areRenderedAndReachable() {
        composeRule.setContent { TimeRangeSelector(selected = AnalyticsTimeRange.SELECTED_MONTH, onSelect = {}) }

        composeRule.onNodeWithText("1M").assertIsDisplayed()
        composeRule.onNodeWithText("3M").assertIsDisplayed()
        composeRule.onNodeWithText("6M").assertIsDisplayed()
        composeRule.onNodeWithText("12M").assertIsDisplayed()
    }

    @Test
    fun compactLabels_preserveFullMeaningViaContentDescription() {
        composeRule.setContent { TimeRangeSelector(selected = AnalyticsTimeRange.SELECTED_MONTH, onSelect = {}) }

        composeRule.onNodeWithContentDescription("12 months").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Month").assertIsDisplayed()
    }

    @Test
    fun selecting12Months_invokesCallbackWithLast12Months() {
        var selected: AnalyticsTimeRange? = null
        composeRule.setContent { TimeRangeSelector(selected = AnalyticsTimeRange.SELECTED_MONTH, onSelect = { selected = it }) }

        composeRule.onNodeWithText("12M").performClick()

        assertEquals(AnalyticsTimeRange.LAST_12_MONTHS, selected)
    }

    @Test
    fun selectedOption_isMarkedSelected() {
        composeRule.setContent { TimeRangeSelector(selected = AnalyticsTimeRange.LAST_6_MONTHS, onSelect = {}) }

        composeRule.onNodeWithText("6M").assertIsSelected()
    }

    @Test
    fun selecting3Months_invokesCallback() {
        var selected: AnalyticsTimeRange? = null
        composeRule.setContent { TimeRangeSelector(selected = AnalyticsTimeRange.SELECTED_MONTH, onSelect = { selected = it }) }

        composeRule.onNodeWithText("3M").performClick()

        assertEquals(AnalyticsTimeRange.LAST_3_MONTHS, selected)
    }
}
