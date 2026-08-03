package com.aradrotem.spendwise.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression test for the bug where a CUSTOM-split expense's overflow menu was effectively
// unreachable (the group-specific "+ Add expense" FAB physically overlapped the last card in the
// list - see GroupDetailsScreen's added bottom content padding). Proves EQUAL and CUSTOM expense
// cards expose identical Edit/Delete/Cancel actions, and that tapping the card body (not just the
// overflow menu) opens Edit for both split methods.
@RunWith(AndroidJUnit4::class)
class GroupExpenseRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun expenseItem(splitMethod: GroupSplitMethod) = GroupExpenseListItem(
        expense = GroupExpenseEntity(
            id = 7L, groupId = 1L, title = "Dinner", amountCents = 4_000L, dateEpochDay = 100L,
            paidByMemberId = 1L, splitMethod = splitMethod
        ),
        payerName = "Ann",
        participantNames = listOf("Ann", "Ben")
    )

    @Test
    fun equalExpense_showsEditDeleteCancelInOverflowMenu() {
        composeRule.setContent {
            GroupExpenseRow(item = expenseItem(GroupSplitMethod.EQUAL), onEdit = {}, onRequestDelete = {})
        }

        composeRule.onNodeWithText("⋮").performClick()

        composeRule.onNodeWithText("Edit").assertExists()
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun customExpense_showsSameOverflowActionsAsEqual() {
        composeRule.setContent {
            GroupExpenseRow(item = expenseItem(GroupSplitMethod.CUSTOM), onEdit = {}, onRequestDelete = {})
        }

        composeRule.onNodeWithText("⋮").performClick()

        composeRule.onNodeWithText("Edit").assertExists()
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun customExpense_tappingCardBody_invokesOnEdit() {
        var editedId: Long? = null
        composeRule.setContent {
            GroupExpenseRow(
                item = expenseItem(GroupSplitMethod.CUSTOM),
                onEdit = { editedId = 7L },
                onRequestDelete = {}
            )
        }

        composeRule.onNodeWithText("Dinner").performClick()

        assertEquals(7L, editedId)
    }

    @Test
    fun equalExpense_tappingCardBody_invokesOnEdit() {
        var edited = false
        composeRule.setContent {
            GroupExpenseRow(item = expenseItem(GroupSplitMethod.EQUAL), onEdit = { edited = true }, onRequestDelete = {})
        }

        composeRule.onNodeWithText("Dinner").performClick()

        assertEquals(true, edited)
    }

    @Test
    fun tappingOverflowButton_doesNotAlsoInvokeOnEdit() {
        var edited = false
        composeRule.setContent {
            GroupExpenseRow(item = expenseItem(GroupSplitMethod.CUSTOM), onEdit = { edited = true }, onRequestDelete = {})
        }

        composeRule.onNodeWithText("⋮").performClick()

        assertEquals(false, edited)
    }

    @Test
    fun overflowMenu_delete_invokesOnRequestDeleteNotOnEdit() {
        var edited = false
        var deleteRequested = false
        composeRule.setContent {
            GroupExpenseRow(
                item = expenseItem(GroupSplitMethod.CUSTOM),
                onEdit = { edited = true },
                onRequestDelete = { deleteRequested = true }
            )
        }

        composeRule.onNodeWithText("⋮").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(true, deleteRequested)
        assertEquals(false, edited)
    }
}
