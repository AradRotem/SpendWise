package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.ExpenseGroupDao
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.local.GroupExpenseDao
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupExpensePendingDeletionDao
import com.aradrotem.spendwise.data.local.GroupExpensePendingDeletionEntity
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import com.aradrotem.spendwise.data.local.GroupMemberDao
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.local.GroupRole
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncMetadataDao
import com.aradrotem.spendwise.data.sync.SyncStamper
import com.aradrotem.spendwise.domain.ReconciledExpense
import com.aradrotem.spendwise.domain.RemoteGroupExpense
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

// Wraps the three closely-coupled group-expense DAOs (a group's members, expenses and shares are
// only ever created/edited/deleted together - see the delete-safety and cross-group checks below).
// Kept as a single repository, mirroring how RecurringPaymentRepository owns its plan DAO, rather
// than splitting into one repository per table. Also owns the shared-group sync-support surface
// SharedGroupSyncEngine drives (getOrCreateLocalGroupForSync onward) - kept here rather than in
// the engine itself so it stays the only place that ever touches these DAOs directly.
class GroupExpenseRepository(
    private val groupDao: ExpenseGroupDao,
    private val memberDao: GroupMemberDao,
    private val expenseDao: GroupExpenseDao,
    private val syncMetadataDao: SyncMetadataDao? = null,
    private val pendingDeletionDao: GroupExpensePendingDeletionDao? = null
) {
    private val groupSyncStamper = SyncStamper(syncMetadataDao, SyncEntityType.GROUP)
    private val memberSyncStamper = SyncStamper(syncMetadataDao, SyncEntityType.GROUP_MEMBER)
    private val expenseSyncStamper = SyncStamper(syncMetadataDao, SyncEntityType.GROUP_EXPENSE)
    private val shareSyncStamper = SyncStamper(syncMetadataDao, SyncEntityType.GROUP_EXPENSE_SHARE)

    fun observeGroups(): Flow<List<ExpenseGroupEntity>> = groupDao.observeAll()

    suspend fun getGroup(id: Long): ExpenseGroupEntity? = groupDao.getById(id)

    fun observeAllMembers(): Flow<List<GroupMemberEntity>> = memberDao.observeAll()

    fun observeMembers(groupId: Long): Flow<List<GroupMemberEntity>> = memberDao.observeByGroup(groupId)

    // Used by the group list screen to compute every group's summary card in one combine().
    fun observeAllExpenses(): Flow<List<GroupExpenseEntity>> = expenseDao.observeAll()

    fun observeAllShares(): Flow<List<GroupExpenseShareEntity>> = expenseDao.observeAllShares()

    fun observeExpenses(groupId: Long): Flow<List<GroupExpenseEntity>> = expenseDao.observeByGroup(groupId)

    fun observeShares(groupId: Long): Flow<List<GroupExpenseShareEntity>> = expenseDao.observeSharesByGroup(groupId)

    suspend fun getExpense(id: Long): GroupExpenseEntity? = expenseDao.getById(id)

    suspend fun getShares(expenseId: Long): List<GroupExpenseShareEntity> = expenseDao.getSharesForExpense(expenseId)

    // Creates a group together with its initial members in one call so the caller never has an
    // intermediate state of "group with zero members" mid-save. Defensive re-validation mirrors
    // AddRecurringPlanViewModel/RecurringPaymentRepository: the form already validates every
    // field, this is the last gate.
    suspend fun createGroup(name: String, memberNames: List<String>): Result<Long> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name is required"))
        }
        val trimmedMemberNames = memberNames.map { it.trim() }.filter { it.isNotBlank() }
        if (hasDuplicateNormalizedNames(trimmedMemberNames)) {
            return Result.failure(IllegalArgumentException("Member names must be unique within a group"))
        }

        val groupId = groupDao.insert(ExpenseGroupEntity(name = trimmedName))
        groupSyncStamper.markDirty(groupId)
        trimmedMemberNames.forEach { memberName ->
            val memberId = memberDao.insert(GroupMemberEntity(groupId = groupId, name = memberName))
            memberSyncStamper.markDirty(memberId)
        }
        return Result.success(groupId)
    }

    // Upgrades a purely local group to a real shared one (Step 19 Part 3) - only sets the two new
    // identity columns; every existing member/expense/settlement row is untouched, so nothing
    // about the group's existing data is affected by this call.
    suspend fun markGroupShared(id: Long, groupSyncId: String, ownerUid: String) {
        val existing = groupDao.getById(id) ?: return
        groupDao.update(existing.copy(groupSyncId = groupSyncId, ownerUid = ownerUid))
        groupSyncStamper.markDirty(id)
    }

    suspend fun renameGroup(id: Long, name: String): Result<Unit> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name is required"))
        }
        val existing = groupDao.getById(id) ?: return Result.failure(IllegalStateException("Group not found"))
        groupDao.update(existing.copy(name = trimmedName))
        groupSyncStamper.markDirty(id)
        return Result.success(Unit)
    }

    // Members/expenses/shares all cascade-delete via their foreign keys, so this single call
    // removes the group's entire dataset.
    suspend fun deleteGroup(group: ExpenseGroupEntity) {
        groupDao.delete(group)
        groupSyncStamper.markDeleted(group.id)
    }

    suspend fun addMember(groupId: Long, name: String): Result<Long> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Member name is required"))
        }
        if (memberDao.countByNormalizedName(groupId, trimmedName) > 0) {
            return Result.failure(IllegalArgumentException("A member with this name already exists in this group"))
        }
        val memberId = memberDao.insert(GroupMemberEntity(groupId = groupId, name = trimmedName))
        memberSyncStamper.markDirty(memberId)
        return Result.success(memberId)
    }

    suspend fun renameMember(memberId: Long, name: String): Result<Unit> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Member name is required"))
        }
        val existing = memberDao.getById(memberId) ?: return Result.failure(IllegalStateException("Member not found"))
        if (memberDao.countByNormalizedName(existing.groupId, trimmedName, excludeId = memberId) > 0) {
            return Result.failure(IllegalArgumentException("A member with this name already exists in this group"))
        }
        memberDao.update(existing.copy(name = trimmedName))
        memberSyncStamper.markDirty(memberId)
        return Result.success(Unit)
    }

    // Safe-delete rule (Step 14 scope): a member can only be removed if they never paid for, and
    // were never a share participant in, any existing group expense - deleting them otherwise
    // would silently rewrite historical expenses. See GroupExpenseEntity/GroupExpenseShareEntity's
    // foreign keys, which back this up at the database level too.
    suspend fun deleteMember(member: GroupMemberEntity): Result<Unit> {
        val paidCount = expenseDao.countExpensesPaidByMember(member.id)
        val shareCount = expenseDao.countSharesForMember(member.id)
        if (paidCount > 0 || shareCount > 0) {
            return Result.failure(
                IllegalStateException("This member cannot be deleted while related expenses exist")
            )
        }
        memberDao.delete(member)
        memberSyncStamper.markDeleted(member.id)
        return Result.success(Unit)
    }

    // shares must contain exactly the participating members (excluded members simply have no
    // entry), each amount must be non-negative, and the total must equal amountCents exactly -
    // all re-checked here as the last gate even though the UI already enforces it live.
    suspend fun createExpense(
        groupId: Long,
        title: String,
        amountCents: Long,
        dateEpochDay: Long,
        paidByMemberId: Long,
        splitMethod: GroupSplitMethod,
        note: String,
        shares: Map<Long, Long>
    ): Result<Long> {
        val validation = validateExpenseInput(groupId, title, amountCents, paidByMemberId, shares)
        if (validation != null) return Result.failure(validation)

        val expenseId = expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId,
                title = title.trim(),
                amountCents = amountCents,
                dateEpochDay = dateEpochDay,
                paidByMemberId = paidByMemberId,
                splitMethod = splitMethod,
                note = note.trim()
            ),
            shares.map { (memberId, shareCents) -> GroupExpenseShareEntity(expenseId = 0, memberId = memberId, shareAmountCents = shareCents) }
        )
        expenseSyncStamper.markDirty(expenseId)
        expenseDao.getSharesForExpense(expenseId).forEach { shareSyncStamper.markDirty(it.id) }
        return Result.success(expenseId)
    }

    suspend fun updateExpense(
        id: Long,
        groupId: Long,
        title: String,
        amountCents: Long,
        dateEpochDay: Long,
        paidByMemberId: Long,
        splitMethod: GroupSplitMethod,
        note: String,
        shares: Map<Long, Long>
    ): Result<Unit> {
        val validation = validateExpenseInput(groupId, title, amountCents, paidByMemberId, shares)
        if (validation != null) return Result.failure(validation)
        val existing = expenseDao.getById(id) ?: return Result.failure(IllegalStateException("Expense not found"))

        expenseDao.updateExpenseWithShares(
            existing.copy(
                title = title.trim(),
                amountCents = amountCents,
                dateEpochDay = dateEpochDay,
                paidByMemberId = paidByMemberId,
                splitMethod = splitMethod,
                note = note.trim()
            ),
            shares.map { (memberId, shareCents) -> GroupExpenseShareEntity(expenseId = id, memberId = memberId, shareAmountCents = shareCents) }
        )
        expenseSyncStamper.markDirty(id)
        // updateExpenseWithShares fully replaces the share rows (delete-then-insert), so every
        // resulting share is re-stamped rather than diffed against the old set.
        expenseDao.getSharesForExpense(id).forEach { shareSyncStamper.markDirty(it.id) }
        return Result.success(Unit)
    }

    suspend fun deleteExpense(expense: GroupExpenseEntity) {
        // A shared-group expense's cloud counterpart must be deleted too - queued BEFORE the local
        // row is gone, since expense.cloudId (and the group's groupSyncId) wouldn't be recoverable
        // afterward. SharedGroupSyncEngine drains this queue at its usual trigger points, same
        // pattern as ReceiptRepository's pending-deletion retry.
        if (expense.cloudId != null && pendingDeletionDao != null) {
            val group = groupDao.getById(expense.groupId)
            val groupSyncId = group?.groupSyncId
            if (groupSyncId != null) {
                pendingDeletionDao.insert(GroupExpensePendingDeletionEntity(groupSyncId = groupSyncId, cloudId = expense.cloudId))
            }
        }

        val shareIds = expenseDao.getSharesForExpense(expense.id).map { it.id }
        expenseDao.deleteExpense(expense)
        expenseSyncStamper.markDeleted(expense.id)
        shareIds.forEach { shareSyncStamper.markDeleted(it) }
    }

    // ---------------------------------------------------------------------------------------
    // SharedGroupSyncEngine support (Step 19 completion pass)
    // ---------------------------------------------------------------------------------------

    // Finds the local mirror row for a shared group discovered via
    // users/{uid}/groupMemberships, or creates one if this is the first time this device has
    // heard of it (e.g. membership was accepted on a different device). Never creates a second
    // local row for a groupSyncId that already has one - even across two overlapping Sync passes
    // that both see no existing row here before either has inserted (a real race, since
    // SpendWiseApplication.sharedGroupSyncEngine hands out a brand new SharedGroupSyncEngine, and
    // therefore a brand new independent Mutex, on every access - resume, connectivity-regained, a
    // manual "Sync now", and Group-Details/Groups-list screen entry can all be in flight at once).
    // The check-then-insert below is only the fast path; expense_groups.groupSyncId also carries a
    // UNIQUE index (see MIGRATION_13_14/ExpenseGroupEntity), so the race's loser gets its insert
    // rejected rather than silently creating a duplicate, and recovers by returning the winner's id
    // instead of its own.
    suspend fun getOrCreateLocalGroupForSync(groupSyncId: String, groupName: String): Long {
        val existing = groupDao.getByGroupSyncId(groupSyncId)
        if (existing != null) return existing.id
        return try {
            val groupId = groupDao.insert(ExpenseGroupEntity(name = groupName, groupSyncId = groupSyncId))
            groupSyncStamper.markDirty(groupId)
            groupId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            groupDao.getByGroupSyncId(groupSyncId)?.id ?: throw e
        }
    }

    // Inserts or updates the local member row for one authenticated cloud member, keyed by
    // memberUid (not name) - this is what prevents creating a duplicate person on every sync pass,
    // and what keeps a real member distinct from any pre-Step-19 local-only (memberUid == null)
    // member even if their names happen to match.
    suspend fun upsertSyncedMember(groupId: Long, memberUid: String, displayName: String, role: GroupRole) {
        val existing = memberDao.getByGroup(groupId).firstOrNull { it.memberUid == memberUid }
        if (existing != null) {
            if (existing.name != displayName || existing.role != role) {
                memberDao.update(existing.copy(name = displayName, role = role))
                memberSyncStamper.markDirty(existing.id)
            }
            return
        }
        val memberId = memberDao.insert(GroupMemberEntity(groupId = groupId, name = displayName, memberUid = memberUid, role = role))
        memberSyncStamper.markDirty(memberId)
    }

    // uid -> local member id, for authenticated members only (memberUid != null) - what
    // GroupExpenseReconciler needs to translate a remote expense's paidByUid/shares into local ids.
    suspend fun getMemberUidMap(groupId: Long): Map<String, Long> =
        memberDao.getByGroup(groupId).mapNotNull { member -> member.memberUid?.let { it to member.id } }.toMap()

    // The reverse mapping, for pushing a locally-created expense's payer/participants as uids.
    suspend fun getMemberLocalIdToUidMap(groupId: Long): Map<Long, String> =
        memberDao.getByGroup(groupId).mapNotNull { member -> member.memberUid?.let { uid -> member.id to uid } }.toMap()

    suspend fun getExpensesByCloudId(groupId: Long): Map<String, GroupExpenseEntity> =
        expenseDao.getByGroupOnce(groupId).mapNotNull { expense -> expense.cloudId?.let { it to expense } }.toMap()

    // Applies GroupExpenseReconciler's output - each entry is either a brand-new local insert
    // (existingLocalId == null) or an in-place update of an already-synced row.
    suspend fun applyReconciledExpenses(reconciled: List<ReconciledExpense>) {
        reconciled.forEach { item ->
            if (item.existingLocalId == null) {
                expenseDao.insertExpenseWithShares(
                    item.entity,
                    item.shares.map { (memberId, cents) -> GroupExpenseShareEntity(expenseId = 0, memberId = memberId, shareAmountCents = cents) }
                )
            } else {
                expenseDao.updateExpenseWithShares(
                    item.entity,
                    item.shares.map { (memberId, cents) -> GroupExpenseShareEntity(expenseId = item.existingLocalId, memberId = memberId, shareAmountCents = cents) }
                )
            }
        }
    }

    // Applies GroupExpenseReconciler.localIdsToDelete - a previously-synced local expense whose
    // cloud counterpart is gone. Plain DAO deletes (not the public deleteExpense above), since
    // there is nothing left to push back to the cloud - the cloud is what's authoritative here.
    suspend fun applyRemoteDeletions(localIds: List<Long>) {
        localIds.forEach { id ->
            val expense = expenseDao.getById(id) ?: return@forEach
            expenseDao.deleteExpense(expense)
        }
    }

    // Shared-group expenses awaiting a cloud push - reuses the SAME dirty-flag mechanism
    // (SyncMetadataDao/SyncStamper) the pre-existing personal SyncEngine already relies on,
    // rather than a second bookkeeping scheme. Because createExpense AND updateExpense both call
    // expenseSyncStamper.markDirty, this single query naturally covers both "never pushed yet"
    // (cloudId == null) and "already pushed once, edited again since" - i.e. this is also what
    // makes an edit on one device propagate to another (see SharedGroupSyncEngine's remote-update
    // support).
    suspend fun getPendingPushExpenses(groupId: Long): List<GroupExpenseEntity> {
        val dirtyIds = syncMetadataDao?.getPendingByType(SyncEntityType.GROUP_EXPENSE.tag)?.map { it.localId }?.toSet().orEmpty()
        if (dirtyIds.isEmpty()) return emptyList()
        return expenseDao.getByGroupOnce(groupId).filter { it.id in dirtyIds }
    }

    suspend fun markExpensePushed(expenseId: Long, cloudId: String, createdByUid: String) {
        expenseDao.markPushed(expenseId, cloudId, createdByUid)
        syncMetadataDao?.clearPending(SyncEntityType.GROUP_EXPENSE.tag, expenseId)
    }

    suspend fun getPendingDeletions(): List<GroupExpensePendingDeletionEntity> = pendingDeletionDao?.getAll().orEmpty()

    suspend fun clearPendingDeletion(entity: GroupExpensePendingDeletionEntity) {
        pendingDeletionDao?.delete(entity)
    }

    private suspend fun validateExpenseInput(
        groupId: Long,
        title: String,
        amountCents: Long,
        paidByMemberId: Long,
        shares: Map<Long, Long>
    ): IllegalArgumentException? {
        if (title.isBlank()) return IllegalArgumentException("Title is required")
        if (amountCents <= 0L) return IllegalArgumentException("Amount must be greater than zero")
        if (shares.isEmpty()) return IllegalArgumentException("At least one participant is required")
        if (shares.values.any { it < 0L }) return IllegalArgumentException("Shares cannot be negative")
        if (shares.values.sum() != amountCents) return IllegalArgumentException("Shares must add up to the total amount")

        val groupMemberIds = memberDao.getByGroup(groupId).map { it.id }.toSet()
        if (paidByMemberId !in groupMemberIds) return IllegalArgumentException("Payer must be a member of this group")
        if (shares.keys.any { it !in groupMemberIds }) return IllegalArgumentException("Participants must be members of this group")

        return null
    }
}

// Case/whitespace-insensitive duplicate check across a not-yet-persisted list of member names -
// used only at group creation, before any of them have ids to exclude themselves by.
private fun hasDuplicateNormalizedNames(names: List<String>): Boolean {
    val normalized = names.map { it.trim().lowercase() }
    return normalized.size != normalized.toSet().size
}
