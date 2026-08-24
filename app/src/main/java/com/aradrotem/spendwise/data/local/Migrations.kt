package com.aradrotem.spendwise.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Adds Step 17's generic sync bookkeeping tables. Both are entirely new, additive tables -
// non-destructive, does not touch any existing data. sync_metadata tracks per-row cloud-sync
// state (one table for all entity types instead of adding sync columns to every one of them);
// legacy_import_map durably records which pre-auth rows have already been copied into a
// per-account database, so the one-time legacy import can resume safely after an interruption.
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_metadata` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entityType` TEXT NOT NULL, " +
                "`localId` INTEGER NOT NULL, " +
                "`syncId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                "`pendingSync` INTEGER NOT NULL DEFAULT 1)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_metadata_entityType_localId` " +
                "ON `sync_metadata` (`entityType`, `localId`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_metadata_syncId` ON `sync_metadata` (`syncId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_metadata_pendingSync` ON `sync_metadata` (`pendingSync`)"
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `legacy_import_map` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entityType` TEXT NOT NULL, " +
                "`legacyLocalId` INTEGER NOT NULL, " +
                "`newLocalId` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_legacy_import_map_entityType_legacyLocalId` " +
                "ON `legacy_import_map` (`entityType`, `legacyLocalId`)"
        )
    }
}

// Step 18: adds at-most-one-receipt-per-transaction support. All new transactions columns are
// nullable/defaulted, so every existing (and legacy-imported) transaction is left with "no
// receipt" - non-destructive, does not touch any existing row's other data. receipt_pending_deletions
// is an entirely new, empty-by-default table.
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptId` TEXT")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptStoragePath` TEXT")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptLocalUri` TEXT")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptMimeType` TEXT")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptUpdatedAt` INTEGER")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptUploadPending` INTEGER NOT NULL DEFAULT 0")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipt_pending_deletions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`storagePath` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

// Step 19: two new, entirely additive dedup-state tables for notifications (budget-threshold
// alerts and recurring-payment reminders) - non-destructive, does not touch any existing data.
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notified_budget_thresholds` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryName` TEXT NOT NULL, " +
                "`yearMonth` TEXT NOT NULL, " +
                "`thresholdType` TEXT NOT NULL, " +
                "`notifiedAtEpochMillis` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_notified_budget_thresholds_categoryName_yearMonth_thresholdType` " +
                "ON `notified_budget_thresholds` (`categoryName`, `yearMonth`, `thresholdType`)"
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notified_recurring_reminders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`planId` INTEGER NOT NULL, " +
                "`scheduledYearMonth` TEXT NOT NULL, " +
                "`notifiedAtEpochMillis` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_notified_recurring_reminders_planId_scheduledYearMonth` " +
                "ON `notified_recurring_reminders` (`planId`, `scheduledYearMonth`)"
        )
    }
}

// Step 19: foreign-currency transaction support. Four new nullable columns on transactions
// (non-destructive - every existing row defaults to "not a foreign-currency entry"), plus a new,
// entirely additive exchange-rate cache table used for offline/API-unavailable fallback.
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `originalAmountCents` INTEGER")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `originalCurrencyCode` TEXT")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `conversionRate` REAL")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `rateTimestampEpochMillis` INTEGER")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_exchange_rates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fromCurrency` TEXT NOT NULL, " +
                "`toCurrency` TEXT NOT NULL, " +
                "`rate` REAL NOT NULL, " +
                "`fetchedAtEpochMillis` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cached_exchange_rates_fromCurrency_toCurrency` " +
                "ON `cached_exchange_rates` (`fromCurrency`, `toCurrency`)"
        )
    }
}

// Step 19: real multi-user shared groups. All new columns are nullable/defaulted, so every
// existing local-only group, member, and expense keeps behaving exactly as before - a group only
// becomes "shared" once its groupSyncId is explicitly set via GroupCloudRepository.shareExistingGroup.
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `groupSyncId` TEXT")
        connection.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `ownerUid` TEXT")

        connection.execSQL("ALTER TABLE `group_members` ADD COLUMN `memberUid` TEXT")
        connection.execSQL("ALTER TABLE `group_members` ADD COLUMN `role` TEXT")

        connection.execSQL("ALTER TABLE `group_expenses` ADD COLUMN `cloudId` TEXT")
        connection.execSQL("ALTER TABLE `group_expenses` ADD COLUMN `createdByUid` TEXT")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_group_expenses_cloudId` ON `group_expenses` (`cloudId`)"
        )
    }
}

// Step 19 completion pass: durable queue for shared-group expense deletions still awaiting their
// cloud counterpart's deletion (see GroupExpensePendingDeletionEntity) - entirely new, additive
// table, non-destructive to any existing data.
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_expense_pending_deletions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`groupSyncId` TEXT NOT NULL, " +
                "`cloudId` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

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

// Fix for a real race in GroupExpenseRepository.getOrCreateLocalGroupForSync: overlapping Sync
// passes (e.g. app resume, a manual "Sync now" tap, and GroupExpensesListScreen's own entry-point
// sync all firing close together - each one via SpendWiseApplication.sharedGroupSyncEngine, a
// computed property that hands out a BRAND NEW SharedGroupSyncEngine, and therefore a brand new
// independent Mutex, on every access) could each find no existing local row for the same cloud
// groupSyncId and insert their own, duplicating one shared group into multiple local rows. First,
// de-duplicate any rows already created by that race - keeping the earliest (lowest id) row per
// distinct non-null groupSyncId and letting its members/expenses/shares cascade-delete with it is
// safe, since they're just redundant copies of what the next Sync re-pulls from Firestore into the
// surviving row anyway. Purely-local groups (groupSyncId IS NULL) are never touched - SQLite's
// UNIQUE index treats every NULL as distinct from every other NULL. Then add the unique index
// itself, so the underlying race can never produce a duplicate again (see
// GroupExpenseRepository.getOrCreateLocalGroupForSync's insert-conflict recovery).
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "DELETE FROM `expense_groups` WHERE `groupSyncId` IS NOT NULL AND `id` NOT IN " +
                "(SELECT MIN(`id`) FROM `expense_groups` WHERE `groupSyncId` IS NOT NULL GROUP BY `groupSyncId`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_groups_groupSyncId` ON `expense_groups` (`groupSyncId`)"
        )
    }
}

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

// Adds the Step 14 group-expense feature: groups, their members, shared expenses, and per-member
// expense shares. Entirely new tables with foreign keys/indices only - non-destructive, does not
// touch any existing transaction/category/budget/recurring-plan/recurring-exception data.
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `expense_groups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL)"
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_members` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)"
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_expenses` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, " +
                "`paidByMemberId` INTEGER NOT NULL, " +
                "`splitMethod` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`paidByMemberId`) REFERENCES `group_members`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_group_expenses_groupId` ON `group_expenses` (`groupId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_group_expenses_paidByMemberId` ON `group_expenses` (`paidByMemberId`)"
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_expense_shares` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`expenseId` INTEGER NOT NULL, " +
                "`memberId` INTEGER NOT NULL, " +
                "`shareAmountCents` INTEGER NOT NULL, " +
                "FOREIGN KEY(`expenseId`) REFERENCES `group_expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`memberId`) REFERENCES `group_members`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_group_expense_shares_expenseId_memberId` " +
                "ON `group_expense_shares` (`expenseId`, `memberId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_group_expense_shares_memberId` ON `group_expense_shares` (`memberId`)"
        )
    }
}
