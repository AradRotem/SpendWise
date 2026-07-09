package com.aradrotem.spendwise.navigation

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", "🏠"),
    BottomNavItem(Screen.Transactions, "Transactions", "💳"),
    BottomNavItem(Screen.Budgets, "Budgets", "💰"),
    BottomNavItem(Screen.Settings, "Settings", "⚙")
)
