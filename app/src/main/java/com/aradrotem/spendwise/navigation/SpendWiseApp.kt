package com.aradrotem.spendwise.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aradrotem.spendwise.ui.screens.AddRecurringPlanScreen
import com.aradrotem.spendwise.ui.screens.AddTransactionScreen
import com.aradrotem.spendwise.ui.screens.BudgetsScreen
import com.aradrotem.spendwise.ui.screens.CategoriesScreen
import com.aradrotem.spendwise.ui.screens.HomeScreen
import com.aradrotem.spendwise.ui.screens.RecurringPaymentsScreen
import com.aradrotem.spendwise.ui.screens.SettingsScreen
import com.aradrotem.spendwise.ui.screens.TransactionsScreen

@Composable
fun SpendWiseApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute != Screen.AddTransaction.route &&
        currentRoute != Screen.EditTransaction.route &&
        currentRoute != Screen.Categories.route &&
        currentRoute != Screen.AddRecurringPayment.route &&
        currentRoute != Screen.EditRecurringPayment.route
    // The Budgets and Recurring Payments screens each have their own "Add" action, so the global
    // "Add transaction" FAB is hidden there to avoid two competing add actions.
    val showFab = showBottomBar && currentRoute != Screen.Budgets.route && currentRoute != Screen.RecurringPayments.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(item.icon) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { navController.navigate(Screen.AddTransaction.route) }) {
                    Text("+")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToBudgets = { navController.navigate(Screen.Budgets.route) },
                    onViewAllTransactions = { navController.navigate(Screen.Transactions.route) }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    onEditTransaction = { transactionId ->
                        navController.navigate(Screen.EditTransaction.createRoute(transactionId))
                    },
                    onEditThisAndFuture = { planId, transactionId ->
                        navController.navigate(Screen.EditRecurringPayment.createRoute(planId, transactionId))
                    },
                    onOpenRecurringPlan = { planId ->
                        navController.navigate(Screen.EditRecurringPayment.createRoute(planId))
                    }
                )
            }
            composable(Screen.Budgets.route) { BudgetsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                    onNavigateToRecurringPayments = { navController.navigate(Screen.RecurringPayments.route) }
                )
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.RecurringPayments.route) {
                RecurringPaymentsScreen(
                    onAddPlan = { navController.navigate(Screen.AddRecurringPayment.route) },
                    onEditPlan = { planId -> navController.navigate(Screen.EditRecurringPayment.createRoute(planId)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddRecurringPayment.route) {
                AddRecurringPlanScreen(
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditRecurringPayment.route,
                arguments = listOf(
                    navArgument("planId") { type = NavType.LongType },
                    navArgument("occurrenceTransactionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val planId = backStackEntry.arguments?.getLong("planId")
                val occurrenceTransactionId = backStackEntry.arguments?.getLong("occurrenceTransactionId")?.takeIf { it != -1L }
                AddRecurringPlanScreen(
                    planId = planId,
                    occurrenceTransactionId = occurrenceTransactionId,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddTransaction.route) {
                AddTransactionScreen(
                    transactionId = null,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditTransaction.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getLong("transactionId")
                AddTransactionScreen(
                    transactionId = transactionId,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
