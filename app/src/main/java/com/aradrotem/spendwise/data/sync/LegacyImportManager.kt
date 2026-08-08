package com.aradrotem.spendwise.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import com.aradrotem.spendwise.data.local.TransactionType
import kotlinx.coroutines.flow.first
import java.util.UUID

// One-time copy of the pre-Step-17 legacy database (no account concept) into a newly logged-in
// account's own per-uid database. Every copied row is recorded in legacy_import_map *in the same
// Room transaction* as the row insert itself (see copyRecord), so a crash/process-death mid-import
// can never duplicate a row on retry: either both the row and its bookkeeping entry exist, or
// neither does.
//
// Claim rule: the legacy database is only ever imported into the FIRST account that logs in after
// a Step 17 install. Any different account logging in later gets a fresh, empty per-uid database
// and never inherits the first account's data - see importIfNeeded.
class LegacyImportManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun importIfNeeded(newUid: String) {
        val claimedBy = prefs.getString(KEY_CLAIMED_BY, null)
        if (claimedBy != null && claimedBy != newUid) return
        if (prefs.getBoolean(completedKey(newUid), false)) return

        val legacyDb = SpendWiseDatabase.getInstance(context, null)
        val targetDb = SpendWiseDatabase.getInstance(context, newUid)

        importCategories(legacyDb, targetDb)

        val planIdMap = copyAll(
            legacyDb.recurringPaymentPlanDao().observeAll().first(),
            SyncEntityType.RECURRING_PLAN,
            targetDb,
            legacyIdOf = { it.id }
        ) { plan -> targetDb.recurringPaymentPlanDao().insert(plan.copy(id = 0)) }

        copyAll(
            legacyDb.transactionDao().observeAll().first(),
            SyncEntityType.TRANSACTION,
            targetDb,
            legacyIdOf = { it.id }
        ) { tx ->
            val remappedPlanId = tx.recurringPlanId?.let { planIdMap[it] }
            targetDb.transactionDao().insert(tx.copy(id = 0, recurringPlanId = remappedPlanId))
        }

        copyAll(
            legacyDb.recurringOccurrenceExceptionDao().observeAll().first(),
            SyncEntityType.RECURRING_EXCEPTION,
            targetDb,
            legacyIdOf = { it.id }
        ) { exception ->
            val remappedPlanId = planIdMap[exception.recurringPlanId] ?: exception.recurringPlanId
            targetDb.recurringOccurrenceExceptionDao().insert(exception.copy(id = 0, recurringPlanId = remappedPlanId))
        }

        copyAll(
            legacyDb.budgetDao().observeAll().first(),
            SyncEntityType.BUDGET,
            targetDb,
            legacyIdOf = { it.id }
        ) { budget -> targetDb.budgetDao().insert(budget.copy(id = 0)) }

        val groupIdMap = copyAll(
            legacyDb.expenseGroupDao().observeAll().first(),
            SyncEntityType.GROUP,
            targetDb,
            legacyIdOf = { it.id }
        ) { group -> targetDb.expenseGroupDao().insert(group.copy(id = 0)) }

        val memberIdMap = copyAll(
            legacyDb.groupMemberDao().observeAll().first(),
            SyncEntityType.GROUP_MEMBER,
            targetDb,
            legacyIdOf = { it.id }
        ) { member ->
            val remappedGroupId = groupIdMap[member.groupId] ?: member.groupId
            targetDb.groupMemberDao().insert(member.copy(id = 0, groupId = remappedGroupId))
        }

        val legacyShares = legacyDb.groupExpenseDao().observeAllShares().first()
        copyAll(
            legacyDb.groupExpenseDao().observeAll().first(),
            SyncEntityType.GROUP_EXPENSE,
            targetDb,
            legacyIdOf = { it.id }
        ) { expense ->
            val remappedGroupId = groupIdMap[expense.groupId] ?: expense.groupId
            val remappedPaidBy = memberIdMap[expense.paidByMemberId] ?: expense.paidByMemberId
            val remappedShares = legacyShares.filter { it.expenseId == expense.id }.map { share ->
                share.copy(id = 0, expenseId = 0, memberId = memberIdMap[share.memberId] ?: share.memberId)
            }
            val newExpenseId = targetDb.groupExpenseDao()
                .insertExpenseWithShares(expense.copy(id = 0, groupId = remappedGroupId, paidByMemberId = remappedPaidBy), remappedShares)

            // Record each copied share's own bookkeeping row now that its new id is known, inside
            // the same outer copyAll transaction as the expense (see copyAll/copyRecord).
            val insertedShares = targetDb.groupExpenseDao().getSharesForExpense(newExpenseId)
            val legacySharesForExpense = legacyShares.filter { it.expenseId == expense.id }
            for (legacyShare in legacySharesForExpense) {
                val remappedMemberId = memberIdMap[legacyShare.memberId] ?: legacyShare.memberId
                val matchingNewShare = insertedShares.firstOrNull { it.memberId == remappedMemberId }
                if (matchingNewShare != null) {
                    stampBookkeeping(targetDb, SyncEntityType.GROUP_EXPENSE_SHARE, legacyShare.id, matchingNewShare.id)
                }
            }
            newExpenseId
        }

        prefs.edit()
            .putString(KEY_CLAIMED_BY, newUid)
            .putBoolean(completedKey(newUid), true)
            .apply()
    }

    private suspend fun importCategories(legacyDb: SpendWiseDatabase, targetDb: SpendWiseDatabase) {
        val legacyCategories = legacyDb.categoryDao().observeByType(TransactionType.EXPENSE).first() +
            legacyDb.categoryDao().observeByType(TransactionType.INCOME).first()

        for (category in legacyCategories.filterNot { it.isBuiltIn }) {
            val alreadyImported = targetDb.legacyImportRecordDao().find(SyncEntityType.CATEGORY.tag, category.id)
            if (alreadyImported != null) continue

            targetDb.withTransaction {
                val newId = targetDb.categoryDao().insert(category.copy(id = 0))
                targetDb.legacyImportRecordDao().insert(
                    LegacyImportRecordEntity(entityType = SyncEntityType.CATEGORY.tag, legacyLocalId = category.id, newLocalId = newId)
                )
                targetDb.syncMetadataDao().upsert(
                    SyncMetadataEntity(
                        entityType = SyncEntityType.CATEGORY.tag, localId = newId,
                        syncId = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis(), pendingSync = true
                    )
                )
            }
        }
    }

    // Copies every item in [legacyRows] not already present in legacy_import_map, atomically
    // pairing the entity insert with its import-map + sync-metadata bookkeeping rows in one Room
    // transaction. Returns the full old-id -> new-id map (including rows already imported by a
    // prior, interrupted run), for use remapping foreign keys in subsequent stages.
    private suspend fun <T> copyAll(
        legacyRows: List<T>,
        type: SyncEntityType,
        targetDb: SpendWiseDatabase,
        legacyIdOf: (T) -> Long,
        insertNew: suspend (T) -> Long
    ): Map<Long, Long> {
        val idMap = mutableMapOf<Long, Long>()
        for (row in legacyRows) {
            val legacyId = legacyIdOf(row)
            val existing = targetDb.legacyImportRecordDao().find(type.tag, legacyId)
            if (existing != null) {
                idMap[legacyId] = existing.newLocalId
                continue
            }
            val newId = targetDb.withTransaction {
                val insertedId = insertNew(row)
                targetDb.legacyImportRecordDao().insert(
                    LegacyImportRecordEntity(entityType = type.tag, legacyLocalId = legacyId, newLocalId = insertedId)
                )
                targetDb.syncMetadataDao().upsert(
                    SyncMetadataEntity(
                        entityType = type.tag, localId = insertedId,
                        syncId = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis(), pendingSync = true
                    )
                )
                insertedId
            }
            idMap[legacyId] = newId
        }
        return idMap
    }

    private suspend fun stampBookkeeping(targetDb: SpendWiseDatabase, type: SyncEntityType, legacyId: Long, newId: Long) {
        targetDb.withTransaction {
            targetDb.legacyImportRecordDao().insert(
                LegacyImportRecordEntity(entityType = type.tag, legacyLocalId = legacyId, newLocalId = newId)
            )
            targetDb.syncMetadataDao().upsert(
                SyncMetadataEntity(
                    entityType = type.tag, localId = newId,
                    syncId = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis(), pendingSync = true
                )
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "spendwise_auth_prefs"
        private const val KEY_CLAIMED_BY = "legacy_import_claimed_by_uid"
        private fun completedKey(uid: String) = "legacy_import_completed_$uid"
    }
}
