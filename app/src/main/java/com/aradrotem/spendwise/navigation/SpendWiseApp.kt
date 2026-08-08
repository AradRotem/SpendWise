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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aradrotem.spendwise.ui.screens.AccountScreen
import com.aradrotem.spendwise.ui.screens.AddRecurringPlanScreen
import com.aradrotem.spendwise.ui.screens.AddTransactionScreen
import com.aradrotem.spendwise.ui.screens.BudgetsScreen
import com.aradrotem.spendwise.ui.screens.ForgotPasswordScreen
import com.aradrotem.spendwise.ui.screens.LoginScreen
import com.aradrotem.spendwise.ui.screens.SignUpScreen
import com.aradrotem.spendwise.ui.screens.SplashDestination
import com.aradrotem.spendwise.ui.screens.SplashScreen
import com.aradrotem.spendwise.ui.screens.CategoriesScreen
import com.aradrotem.spendwise.ui.screens.GroupDetailsScreen
import com.aradrotem.spendwise.ui.screens.GroupExpenseFormScreen
import com.aradrotem.spendwise.ui.screens.GroupExpensesListScreen
import com.aradrotem.spendwise.ui.screens.GroupFormScreen
import com.aradrotem.spendwise.ui.screens.GroupSettlementScreen
import com.aradrotem.spendwise.ui.screens.HomeScreen
import com.aradrotem.spendwise.ui.screens.RecurringPaymentsScreen
import com.aradrotem.spendwise.ui.screens.ReportsAnalyticsScreen
import com.aradrotem.spendwise.ui.screens.SettingsScreen
import com.aradrotem.spendwise.ui.screens.TransactionsScreen

@Composable
fun SpendWiseApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = isBottomBarVisible(currentRoute)
    val showFab = isFabVisible(currentRoute)

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
                            icon = { Text(item.icon, modifier = Modifier.clearAndSetSemantics {}) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.AddTransaction.route) { launchSingleTop = true } },
                    modifier = Modifier.semantics { contentDescription = "Add transaction" }
                ) {
                    Text("+")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onResolved = { destination ->
                        val target = if (destination == SplashDestination.HOME) Screen.Home.route else Screen.Login.route
                        navController.navigate(target) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) { launchSingleTop = true } },
                    onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) { launchSingleTop = true } }
                )
            }
            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onSignUpSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Account.route) {
                AccountScreen(
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToBudgets = { navController.navigate(Screen.Budgets.route) { launchSingleTop = true } },
                    onViewAllTransactions = { navController.navigate(Screen.Transactions.route) { launchSingleTop = true } }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    onEditTransaction = { transactionId ->
                        navController.navigate(Screen.EditTransaction.createRoute(transactionId)) { launchSingleTop = true }
                    },
                    onEditThisAndFuture = { planId, transactionId ->
                        navController.navigate(Screen.EditRecurringPayment.createRoute(planId, transactionId)) { launchSingleTop = true }
                    },
                    onOpenRecurringPlan = { planId ->
                        navController.navigate(Screen.EditRecurringPayment.createRoute(planId)) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Budgets.route) { BudgetsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) { launchSingleTop = true } },
                    onNavigateToRecurringPayments = { navController.navigate(Screen.RecurringPayments.route) { launchSingleTop = true } },
                    onNavigateToReportsAnalytics = { navController.navigate(Screen.ReportsAnalytics.route) { launchSingleTop = true } },
                    onNavigateToGroupExpenses = { navController.navigate(Screen.GroupExpenses.route) { launchSingleTop = true } },
                    onNavigateToAccount = { navController.navigate(Screen.Account.route) { launchSingleTop = true } }
                )
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ReportsAnalytics.route) {
                ReportsAnalyticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.RecurringPayments.route) {
                RecurringPaymentsScreen(
                    onAddPlan = { navController.navigate(Screen.AddRecurringPayment.route) { launchSingleTop = true } },
                    onEditPlan = { planId -> navController.navigate(Screen.EditRecurringPayment.createRoute(planId)) { launchSingleTop = true } },
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
            composable(Screen.GroupExpenses.route) {
                GroupExpensesListScreen(
                    onAddGroup = { navController.navigate(Screen.AddGroup.route) { launchSingleTop = true } },
                    onEditGroup = { groupId -> navController.navigate(Screen.EditGroup.createRoute(groupId)) { launchSingleTop = true } },
                    onOpenGroup = { groupId -> navController.navigate(Screen.GroupDetails.createRoute(groupId)) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddGroup.route) {
                GroupFormScreen(
                    groupId = null,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditGroup.route,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: return@composable
                GroupFormScreen(
                    groupId = groupId,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.GroupDetails.route,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: return@composable
                GroupDetailsScreen(
                    groupId = groupId,
                    onAddExpense = { navController.navigate(Screen.AddGroupExpense.createRoute(groupId)) { launchSingleTop = true } },
                    onEditExpense = { expenseId -> navController.navigate(Screen.EditGroupExpense.createRoute(groupId, expenseId)) { launchSingleTop = true } },
                    onViewSettlement = { navController.navigate(Screen.GroupSettlement.createRoute(groupId)) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.GroupSettlement.route,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: return@composable
                GroupSettlementScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AddGroupExpense.route,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: return@composable
                GroupExpenseFormScreen(
                    groupId = groupId,
                    expenseId = null,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditGroupExpense.route,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.LongType },
                    navArgument("expenseId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: return@composable
                val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: return@composable
                GroupExpenseFormScreen(
                    groupId = groupId,
                    expenseId = expenseId,
                    onSaveSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
