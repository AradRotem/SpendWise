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
import com.aradrotem.spendwise.ui.screens.AddTransactionScreen
import com.aradrotem.spendwise.ui.screens.BudgetsScreen
import com.aradrotem.spendwise.ui.screens.HomeScreen
import com.aradrotem.spendwise.ui.screens.SettingsScreen
import com.aradrotem.spendwise.ui.screens.TransactionsScreen

@Composable
fun SpendWiseApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showMainChrome = currentRoute != Screen.AddTransaction.route &&
        currentRoute != Screen.EditTransaction.route

    Scaffold(
        bottomBar = {
            if (showMainChrome) {
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
            if (showMainChrome) {
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
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    onEditTransaction = { transactionId ->
                        navController.navigate(Screen.EditTransaction.createRoute(transactionId))
                    }
                )
            }
            composable(Screen.Budgets.route) { BudgetsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
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
