package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression test for Step 15's final refinement: Settings must expose exactly one
// "Reports & analytics" entry, replacing the old separate "Monthly report" and "Visual analytics"
// entries.
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(onNavigateToReportsAnalytics: () -> Unit = {}) {
        composeRule.setContent {
            SettingsScreen(
                onNavigateToCategories = {},
                onNavigateToRecurringPayments = {},
                onNavigateToReportsAnalytics = onNavigateToReportsAnalytics,
                onNavigateToGroupExpenses = {},
                onNavigateToAccount = {}
            )
        }
    }

    @Test
    fun reportsAnalyticsEntry_isDisplayed() {
        setContent()

        composeRule.onNodeWithText("Reports & analytics").assertIsDisplayed()
    }

    @Test
    fun oldDuplicateEntries_areNoLongerPresent() {
        setContent()

        assertThrows(AssertionError::class.java) { composeRule.onNodeWithText("Monthly report").assertIsDisplayed() }
        assertThrows(AssertionError::class.java) { composeRule.onNodeWithText("Visual analytics").assertIsDisplayed() }
    }

    @Test
    fun tappingReportsAnalytics_invokesCallback() {
        var invoked = false
        setContent(onNavigateToReportsAnalytics = { invoked = true })

        composeRule.onNodeWithText("Reports & analytics").performClick()

        assertEquals(true, invoked)
    }

    // Regression test: the Settings screen used a plain fillMaxSize Column with no scroll modifier,
    // so on a short viewport the lower notification rows (in particular "Shared-group
    // notifications", the last one) were clipped off-screen and unreachable - see SettingsScreen's
    // verticalScroll fix. A fixed small-height Box stands in for a short/constrained device screen.
    @Test
    fun lastNotificationSetting_isReachableByScrollingOnAShortScreen() {
        composeRule.setContent {
            Box(modifier = Modifier.height(400.dp)) {
                SettingsScreen(
                    onNavigateToCategories = {},
                    onNavigateToRecurringPayments = {},
                    onNavigateToReportsAnalytics = {},
                    onNavigateToGroupExpenses = {},
                    onNavigateToAccount = {}
                )
            }
        }

        composeRule.onNodeWithText("Shared-group notifications").performScrollTo().assertIsDisplayed()
    }
}
