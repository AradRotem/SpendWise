package com.aradrotem.spendwise.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Transactions : Screen("transactions")
    data object Budgets : Screen("budgets")
    data object Settings : Screen("settings")
    data object AddTransaction : Screen("add_transaction")
}
