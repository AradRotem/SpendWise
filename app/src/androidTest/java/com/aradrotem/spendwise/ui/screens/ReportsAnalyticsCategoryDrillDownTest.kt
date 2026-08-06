package com.aradrotem.spendwise.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aradrotem.spendwise.domain.CategoryExpenseDetails
import com.aradrotem.spendwise.domain.CategorySpendingPoint
import com.aradrotem.spendwise.domain.DisplayedCategoryTransaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Compose UI tests for the "Spending by category" drill-down (tap a category row to see its
// expenses), exercised in isolation without an Application/database dependency.
@RunWith(AndroidJUnit4::class)
class ReportsAnalyticsCategoryDrillDownTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val foodPoint = CategorySpendingPoint("FOOD", 5_000L, 2, 100f)

    private fun details(count: Int, limited: Boolean = false, hidden: Int = 0) = CategoryExpenseDetails(
        category = "FOOD",
        categoryDisplayName = "Food",
        totalAmountCents = 5_000L,
        transactionCount = count,
        periodLabel = "July 2026",
        displayedTransactions = (1..minOf(count, 5)).map {
            DisplayedCategoryTransaction(it.toLong(), "Groceries $it", 1_000L, 1_000L)
        },
        isLimited = limited,
        hiddenTransactionCount = hidden
    )

    @Test
    fun categoryRow_isClickable_andInvokesCallback() {
        var selected: String? = null
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = null, details = null, onSelectCategory = { selected = it })
        }

        composeRule.onNodeWithText("Food").performClick()

        assertEquals("FOOD", selected)
    }

    @Test
    fun selectedCategory_showsSelectedState() {
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = "FOOD", details = details(2), onSelectCategory = {})
        }

        composeRule.onNodeWithText("Food").assertIsSelected()
        composeRule.onNodeWithText("✓").assertIsDisplayed()
    }

    @Test
    fun detailsSection_appearsAfterSelection() {
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = "FOOD", details = details(2), onSelectCategory = {})
        }

        composeRule.onNodeWithText("Food expenses").assertIsDisplayed()
        composeRule.onNodeWithText("50.00 across 2 transactions").assertIsDisplayed()
    }

    @Test
    fun noDetailsCard_whenNothingSelected() {
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = null, details = null, onSelectCategory = {})
        }

        composeRule.onNodeWithText("Food expenses").assertDoesNotExist()
    }

    @Test
    fun upToFiveTransactionRows_areShown() {
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = "FOOD", details = details(5), onSelectCategory = {})
        }

        composeRule.onNodeWithText("Groceries 1").assertIsDisplayed()
        composeRule.onNodeWithText("Groceries 5").assertIsDisplayed()
    }

    @Test
    fun showingLargestMessage_appearsWhenLimited() {
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = "FOOD", details = details(12, limited = true, hidden = 7), onSelectCategory = {})
        }

        composeRule.onNodeWithText("Showing the 5 largest of 12 expenses.").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessage_appearsWhenNoMatchingTransactions() {
        val emptyDetails = CategoryExpenseDetails(
            category = "FOOD", categoryDisplayName = "Food", totalAmountCents = 0L, transactionCount = 0,
            periodLabel = "July 2026", displayedTransactions = emptyList(), isLimited = false, hiddenTransactionCount = 0
        )
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = "FOOD", details = emptyDetails, onSelectCategory = {})
        }

        composeRule.onNodeWithText("No expenses found for this category in the selected period.").assertIsDisplayed()
    }

    @Test
    fun tappingAnotherCategory_replacesDisplayedDetails() {
        val transportPoint = CategorySpendingPoint("TRANSPORT", 2_000L, 1, 40f)
        var selected: String? = null
        composeRule.setContent {
            SpendingByCategorySection(
                points = listOf(foodPoint, transportPoint), emptyMessage = "none",
                selectedCategory = "FOOD", details = details(2), onSelectCategory = { selected = it }
            )
        }

        composeRule.onNodeWithText("Transport").performClick()

        assertEquals("TRANSPORT", selected)
    }

    @Test
    fun longTransactionTitles_doNotBreakLayout() {
        val longTitleDetails = details(1).copy(
            displayedTransactions = listOf(
                DisplayedCategoryTransaction(1L, "A very long mixed Hebrew and English title קניות משפחתיות לחודש יולי with extra words", 1_000L, 1_000L)
            )
        )
        composeRule.setContent {
            SpendingByCategorySection(points = listOf(foodPoint), emptyMessage = "none", selectedCategory = "FOOD", details = longTitleDetails, onSelectCategory = {})
        }

        composeRule.onNodeWithText("Food expenses").assertIsDisplayed()
    }
}
