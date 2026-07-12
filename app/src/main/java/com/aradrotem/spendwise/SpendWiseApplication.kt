package com.aradrotem.spendwise

import android.app.Application
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import com.aradrotem.spendwise.data.repository.TransactionRepository

class SpendWiseApplication : Application() {

    private val database: SpendWiseDatabase by lazy { SpendWiseDatabase.getInstance(this) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao())
    }
}
