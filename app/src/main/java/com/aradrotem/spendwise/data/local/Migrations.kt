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

// Adds recurring-payment plans plus the nullable link columns on transactions that connect a
// generated transaction back to its plan. Non-destructive: only creates new schema objects and
// adds nullable/defaulted columns, so all existing transaction/category/budget rows are
// preserved untouched.
// `recurring_payment_plans.type` is stored as plain TEXT, so adding the MONTHLY_SALARY plan type
// (added after this migration first shipped) needed no schema change here - only a new enum
// constant value, no new columns or tables.
//
// IMPORTANT: this is the exact schema already installed as "version 4" on physical test devices
// that ran the original Step 10 build. Do not add columns here (e.g. sourceTitle) - once a
// device's database is stamped version 4, Room will never re-run this migration, so any later
// additions belong in MIGRATION_4_5 instead. See that migration for why.
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_payment_plans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`categoryName` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`amountInCents` INTEGER, " +
                "`totalAmountInCents` INTEGER, " +
                "`totalInstallments` INTEGER, " +
                "`firstPaymentDateMillis` INTEGER NOT NULL, " +
                "`preferredDayOfMonth` INTEGER NOT NULL, " +
                "`endDateMillis` INTEGER, " +
                "`status` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )

        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurringPlanId` INTEGER")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `installmentNumber` INTEGER")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `totalInstallments` INTEGER")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `isAutomaticallyGenerated` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `scheduledYearMonth` TEXT")

        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_recurringPlanId_scheduledYearMonth` " +
                "ON `transactions` (`recurringPlanId`, `scheduledYearMonth`)"
        )
    }
}

// Adds the recurring-plan title snapshot column, introduced after MIGRATION_3_4 had already
// shipped to test devices (see that migration's comment for why this couldn't just be folded
// into it). Non-destructive: only adds one nullable column, so all existing rows - including
// those with no opinion on sourceTitle - are preserved untouched.
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `sourceTitle` TEXT")
    }
}

// Adds occurrence-level management (Step 11): a durable exception table recording plan+month
// combinations the user intentionally deleted/skipped (so catch-up generation never recreates
// them - deleting the transaction row alone would free up its unique slot and let it come back),
// plus a flag marking a transaction as individually edited (so a later plan-wide "edit this and
// future" never clobbers a deliberate per-occurrence override). Non-destructive: only creates a
// new table and adds one nullable/defaulted column.
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_occurrence_exceptions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recurringPlanId` INTEGER NOT NULL, " +
                "`scheduledYearMonth` TEXT NOT NULL, " +
                "`exceptionType` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrence_exceptions_recurringPlanId_scheduledYearMonth` " +
                "ON `recurring_occurrence_exceptions` (`recurringPlanId`, `scheduledYearMonth`)"
        )

        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `isOccurrenceModified` INTEGER NOT NULL DEFAULT 0")
    }
}
