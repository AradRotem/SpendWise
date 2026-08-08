package com.aradrotem.spendwise.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object SignUp : Screen("signup")
    data object ForgotPassword : Screen("forgot_password")
    data object Account : Screen("account")
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

    // Single user-facing route for the merged Monthly Report + Visual Analytics feature (Step 15
    // final refinement). "visual_analytics" was the pre-merge route name; kept as the route string
    // rather than renamed, since it's an internal identifier with no user-visible effect - only
    // the Settings entry/screen title changed to "Reports & analytics".
    data object ReportsAnalytics : Screen("visual_analytics")

    data object GroupExpenses : Screen("group_expenses")
    data object AddGroup : Screen("add_group")
    data object EditGroup : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: Long) = "edit_group/$groupId"
    }
    data object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: Long) = "group_details/$groupId"
    }
    data object GroupSettlement : Screen("group_settlement/{groupId}") {
        fun createRoute(groupId: Long) = "group_settlement/$groupId"
    }
    data object AddGroupExpense : Screen("add_group_expense/{groupId}") {
        fun createRoute(groupId: Long) = "add_group_expense/$groupId"
    }
    data object EditGroupExpense : Screen("edit_group_expense/{groupId}/{expenseId}") {
        fun createRoute(groupId: Long, expenseId: Long) = "edit_group_expense/$groupId/$expenseId"
    }
}
