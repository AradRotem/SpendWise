package com.aradrotem.spendwise.data.local

// Retained as a stable historical reference for the category names seeded by MIGRATION_1_2
// (see Migrations.kt), not as the runtime representation of a transaction's category anymore.
enum class TransactionCategory {
    FOOD,
    TRANSPORT,
    HOUSING,
    UTILITIES,
    ENTERTAINMENT,
    HEALTH,
    SHOPPING,
    SALARY,
    OTHER
}
