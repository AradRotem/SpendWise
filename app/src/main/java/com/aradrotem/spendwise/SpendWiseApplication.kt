package com.aradrotem.spendwise

import android.app.Application
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.RecurringPaymentGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendWiseApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: SpendWiseDatabase by lazy { SpendWiseDatabase.getInstance(this) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoryDao())
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(database.budgetDao())
    }

    val recurringPaymentRepository: RecurringPaymentRepository by lazy {
        RecurringPaymentRepository(database.recurringPaymentPlanDao())
    }

    val recurringPaymentGenerator: RecurringPaymentGenerator by lazy {
        RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository)
    }

    override fun onCreate() {
        super.onCreate()
        // Safety net for fresh installs: MIGRATION_1_2 seeds built-ins for upgrading users,
        // but a brand-new v2 database has no migration to run, so it needs its own seeding.
        // Idempotent (see CategoryRepository.ensureBuiltInCategoriesSeeded), so safe on every launch.
        applicationScope.launch {
            categoryRepository.ensureBuiltInCategoriesSeeded()
        }
        // Generation is idempotent and runs off the main thread, so app start is never blocked
        // and re-running it (e.g. after creating a plan) never duplicates transactions.
        applicationScope.launch {
            recurringPaymentGenerator.generateDuePayments()
        }
    }
}
