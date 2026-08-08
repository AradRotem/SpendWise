package com.aradrotem.spendwise.data.sync

// Stable string tags identifying each syncable Room entity type. Stored as plain TEXT in
// sync_metadata/legacy_import_map rather than referencing the entity class, so renaming a Kotlin
// class never silently breaks already-persisted rows.
enum class SyncEntityType(val tag: String) {
    TRANSACTION("transaction"),
    CATEGORY("category"),
    BUDGET("budget"),
    RECURRING_PLAN("recurring_plan"),
    RECURRING_EXCEPTION("recurring_exception"),
    GROUP("group"),
    GROUP_MEMBER("group_member"),
    GROUP_EXPENSE("group_expense"),
    GROUP_EXPENSE_SHARE("group_expense_share");

    companion object {
        fun fromTag(tag: String): SyncEntityType =
            entries.first { it.tag == tag }
    }
}
