package com.aradrotem.spendwise.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Explicit, stable seed data for the built-in categories. Intentionally not derived from
// TransactionCategory, since that enum may change independently of this historical migration.
internal data class BuiltInCategorySeed(
    val name: String,
    val normalizedName: String,
    val type: String
)

internal val builtInCategorySeeds = listOf(
    BuiltInCategorySeed("FOOD", "food", "EXPENSE"),
    BuiltInCategorySeed("TRANSPORT", "transport", "EXPENSE"),
    BuiltInCategorySeed("HOUSING", "housing", "EXPENSE"),
    BuiltInCategorySeed("UTILITIES", "utilities", "EXPENSE"),
    BuiltInCategorySeed("ENTERTAINMENT", "entertainment", "EXPENSE"),
    BuiltInCategorySeed("HEALTH", "health", "EXPENSE"),
    BuiltInCategorySeed("SHOPPING", "shopping", "EXPENSE"),
    BuiltInCategorySeed("OTHER", "other", "EXPENSE"),
    BuiltInCategorySeed("SALARY", "salary", "INCOME"),
    BuiltInCategorySeed("OTHER", "other", "INCOME")
)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`normalizedName` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`isBuiltIn` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_normalizedName_type` " +
                "ON `categories` (`normalizedName`, `type`)"
        )

        for (seed in builtInCategorySeeds) {
            connection.execSQL(
                "INSERT INTO `categories` (`name`, `normalizedName`, `type`, `isBuiltIn`) " +
                    "VALUES ('${seed.name}', '${seed.normalizedName}', '${seed.type}', 1)"
            )
        }
    }
}

// Adds the budgets table. Non-destructive: only creates new schema objects, does not touch
// existing transactions/categories data, and does not seed any default budget rows.
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryName` TEXT NOT NULL, " +
                "`monthlyLimitCents` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryName` " +
                "ON `budgets` (`categoryName`)"
        )
    }
}
