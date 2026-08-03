package com.aradrotem.spendwise.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

// Regression coverage for the navigation bug where Group Details/Add Expense were unreachable
// from the group list: verifies each route-building helper embeds the correct groupId (and
// expenseId), matching what SpendWiseApp.kt extracts back out via NavType.LongType arguments.
class GroupExpenseRoutesTest {

    @Test
    fun groupDetailsRoute_embedsGroupId() {
        assertEquals("group_details/42", Screen.GroupDetails.createRoute(42L))
    }

    @Test
    fun addGroupExpenseRoute_embedsGroupId() {
        assertEquals("add_group_expense/42", Screen.AddGroupExpense.createRoute(42L))
    }

    @Test
    fun editGroupExpenseRoute_embedsGroupIdAndExpenseId() {
        assertEquals("edit_group_expense/42/7", Screen.EditGroupExpense.createRoute(42L, 7L))
    }

    @Test
    fun editGroupRoute_embedsGroupId() {
        assertEquals("edit_group/42", Screen.EditGroup.createRoute(42L))
    }

    @Test
    fun groupSettlementRoute_embedsGroupId() {
        assertEquals("group_settlement/42", Screen.GroupSettlement.createRoute(42L))
    }
}
