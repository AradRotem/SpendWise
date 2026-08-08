package com.aradrotem.spendwise.navigation

// Routes reached by drilling into a bottom-nav destination (a form, a detail screen, a nested
// list) rather than being a top-level destination themselves - the global bottom bar is hidden on
// all of them so the screen's own back button is the only way back.
private val ROUTES_WITHOUT_BOTTOM_BAR = setOf(
    Screen.Splash.route,
    Screen.Login.route,
    Screen.SignUp.route,
    Screen.ForgotPassword.route,
    Screen.Account.route,
    Screen.AddTransaction.route,
    Screen.EditTransaction.route,
    Screen.Categories.route,
    Screen.AddRecurringPayment.route,
    Screen.EditRecurringPayment.route,
    Screen.RecurringPayments.route,
    Screen.ReportsAnalytics.route,
    Screen.GroupExpenses.route,
    Screen.AddGroup.route,
    Screen.EditGroup.route,
    Screen.GroupDetails.route,
    Screen.GroupSettlement.route,
    Screen.AddGroupExpense.route,
    Screen.EditGroupExpense.route
)

// Routes that already have their own dedicated "add" action, where the global "Add transaction"
// FAB would be a second, competing add action.
private val ROUTES_WITH_OWN_ADD_ACTION = setOf(
    Screen.Budgets.route,
    Screen.RecurringPayments.route
)

// Extracted as plain functions (rather than left inline in SpendWiseApp) so this visibility logic
// is directly unit-testable without Compose, matching the rest of the codebase's convention.
fun isBottomBarVisible(route: String?): Boolean = route !in ROUTES_WITHOUT_BOTTOM_BAR

fun isFabVisible(route: String?): Boolean = isBottomBarVisible(route) && route !in ROUTES_WITH_OWN_ADD_ACTION
