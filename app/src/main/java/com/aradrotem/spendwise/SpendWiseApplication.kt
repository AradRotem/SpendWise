package com.aradrotem.spendwise

import android.app.Application
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
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

    override fun onCreate() {
        super.onCreate()
        // Safety net for fresh installs: MIGRATION_1_2 seeds built-ins for upgrading users,
        // but a brand-new v2 database has no migration to run, so it needs its own seeding.
        // Idempotent (see CategoryRepository.ensureBuiltInCategoriesSeeded), so safe on every launch.
        applicationScope.launch {
            categoryRepository.ensureBuiltInCategoriesSeeded()
        }
    }
}
