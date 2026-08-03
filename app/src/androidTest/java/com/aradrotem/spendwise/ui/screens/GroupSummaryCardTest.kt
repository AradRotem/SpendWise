package com.aradrotem.spendwise.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Regression test for the bug where only the card's top row (name + overflow button) was
// clickable - the rest of the card (member count, total spent, settled state) had no click
// handler at all, so a tap anywhere else on the card silently did nothing. Verifies the whole
// card opens Group Details on tap or long press, and that the overflow menu's own taps never
// also trigger that navigation.
@RunWith(AndroidJUnit4::class)
class GroupSummaryCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val summary = GroupSummary(
        group = ExpenseGroupEntity(id = 42L, name = "Trip"),
        memberCount = 3,
        expenseCount = 0,
        totalSpentCents = 0L,
        isSettled = true
    )

    @Test
    fun tappingCardBody_awayFromOverflowButton_invokesOnClick() {
        var clicked = false
        composeRule.setContent {
            GroupSummaryCard(item = summary, onClick = { clicked = true }, onEdit = {}, onRequestDelete = {})
        }

        composeRule.onNodeWithText("Total spent: 0.00").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun longPressingCardBody_alsoInvokesOnClick() {
        var clicked = false
        composeRule.setContent {
            GroupSummaryCard(item = summary, onClick = { clicked = true }, onEdit = {}, onRequestDelete = {})
        }

        composeRule.onNodeWithText("Settled").performTouchInput { longClick() }

        assertEquals(true, clicked)
    }

    @Test
    fun openingOverflowMenu_doesNotInvokeOnClick() {
        var clicked = false
        composeRule.setContent {
            GroupSummaryCard(item = summary, onClick = { clicked = true }, onEdit = {}, onRequestDelete = {})
        }

        composeRule.onNodeWithText("⋮").performClick()

        assertEquals(false, clicked)
    }

    @Test
    fun overflowMenu_editGroup_invokesOnEditNotOnClick() {
        var clicked = false
        var editedGroupId: Long? = null
        composeRule.setContent {
            GroupSummaryCard(
                item = summary,
                onClick = { clicked = true },
                onEdit = { editedGroupId = summary.group.id },
                onRequestDelete = {}
            )
        }

        composeRule.onNodeWithText("⋮").performClick()
        composeRule.onNodeWithText("Edit group").performClick()

        assertEquals(42L, editedGroupId)
        assertEquals(false, clicked)
    }

    @Test
    fun overflowMenu_cancel_invokesNeitherCallback() {
        var clicked = false
        var deleteRequested = false
        composeRule.setContent {
            GroupSummaryCard(
                item = summary,
                onClick = { clicked = true },
                onEdit = {},
                onRequestDelete = { deleteRequested = true }
            )
        }

        composeRule.onNodeWithText("⋮").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(false, clicked)
        assertEquals(false, deleteRequested)
    }
}
