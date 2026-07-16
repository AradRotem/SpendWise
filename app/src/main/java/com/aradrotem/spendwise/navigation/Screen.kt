package com.aradrotem.spendwise.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Transactions : Screen("transactions")
    data object Budgets : Screen("budgets")
    data object Settings : Screen("settings")
    data object AddTransaction : Screen("add_transaction")
    data object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "edit_transaction/$transactionId"
    }
    data object Categories : Screen("categories")
    data object RecurringPayments : Screen("recurring_payments")
    data object AddRecurringPayment : Screen("add_recurring_payment")
    data object EditRecurringPayment : Screen("edit_recurring_payment/{planId}") {
        fun createRoute(planId: Long) = "edit_recurring_payment/$planId"
    }
}
