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
    data object MonthlyReport : Screen("monthly_report")
    data object RecurringPayments : Screen("recurring_payments")
    data object AddRecurringPayment : Screen("add_recurring_payment")
    data object EditRecurringPayment : Screen("edit_recurring_payment/{planId}?occurrenceTransactionId={occurrenceTransactionId}") {
        // occurrenceTransactionId is set only for "Edit this and future transactions" (see
        // TransactionsScreen): it tells the edit screen to also propagate the plan edit to that
        // specific occurrence and later non-overridden ones. Absent for a plain "Edit plan" or
        // "Open recurring plan" navigation.
        fun createRoute(planId: Long, occurrenceTransactionId: Long? = null): String {
            val base = "edit_recurring_payment/$planId"
            return if (occurrenceTransactionId != null) "$base?occurrenceTransactionId=$occurrenceTransactionId" else base
        }
    }
}
