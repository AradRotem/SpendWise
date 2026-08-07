package com.aradrotem.spendwise.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression coverage for the bug where RecurringPaymentsScreen showed the bottom bar alongside
// its own back button, unlike every other Settings sub-screen - and general coverage for the
// showBottomBar/showFab rules now that they're extracted into plain, testable functions.
class NavigationVisibilityTest {

    @Test
    fun bottomBar_hiddenOnEverySettingsSubScreen() {
        val subScreens = listOf(
            Screen.Categories.route,
            Screen.RecurringPayments.route,
            Screen.ReportsAnalytics.route,
            Screen.GroupExpenses.route
        )
        subScreens.forEach { route -> assertFalse(route, isBottomBarVisible(route)) }
    }

    @Test
    fun bottomBar_hiddenOnEveryFormAndDetailRoute() {
        val nonTopLevelRoutes = listOf(
            Screen.AddTransaction.route,
            Screen.EditTransaction.route,
            Screen.AddRecurringPayment.route,
            Screen.EditRecurringPayment.route,
            Screen.AddGroup.route,
            Screen.EditGroup.route,
            Screen.GroupDetails.route,
            Screen.GroupSettlement.route,
            Screen.AddGroupExpense.route,
            Screen.EditGroupExpense.route
        )
        nonTopLevelRoutes.forEach { route -> assertFalse(route, isBottomBarVisible(route)) }
    }

    @Test
    fun bottomBar_visibleOnEveryTopLevelDestination() {
        val topLevelRoutes = listOf(Screen.Home.route, Screen.Transactions.route, Screen.Budgets.route, Screen.Settings.route)
        topLevelRoutes.forEach { route -> assertTrue(route, isBottomBarVisible(route)) }
    }

    @Test
    fun bottomBar_visibleWhenRouteIsNull() {
        assertTrue(isBottomBarVisible(null))
    }

    @Test
    fun fab_hiddenOnScreensWithTheirOwnAddAction() {
        assertFalse(isFabVisible(Screen.Budgets.route))
        assertFalse(isFabVisible(Screen.RecurringPayments.route))
    }

    @Test
    fun fab_hiddenWhereverBottomBarIsHidden() {
        assertFalse(isFabVisible(Screen.GroupDetails.route))
        assertFalse(isFabVisible(Screen.ReportsAnalytics.route))
    }

    @Test
    fun fab_visibleOnHomeAndTransactions() {
        assertTrue(isFabVisible(Screen.Home.route))
        assertTrue(isFabVisible(Screen.Transactions.route))
    }
}
