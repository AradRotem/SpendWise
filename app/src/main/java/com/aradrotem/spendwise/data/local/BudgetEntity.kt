package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Budgets are only ever created for EXPENSE categories; enforced in BudgetRepository, not here.
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryName"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val monthlyLimitCents: Long
)
