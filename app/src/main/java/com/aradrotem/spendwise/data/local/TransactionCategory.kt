package com.aradrotem.spendwise.data.local

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

fun categoriesForType(type: TransactionType): List<TransactionCategory> = when (type) {
    TransactionType.INCOME -> listOf(TransactionCategory.SALARY, TransactionCategory.OTHER)
    TransactionType.EXPENSE -> listOf(
        TransactionCategory.FOOD,
        TransactionCategory.TRANSPORT,
        TransactionCategory.HOUSING,
        TransactionCategory.UTILITIES,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.HEALTH,
        TransactionCategory.SHOPPING,
        TransactionCategory.OTHER
    )
}
