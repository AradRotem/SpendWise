package com.aradrotem.spendwise.data.sync

import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.notifications.NotificationChannels
import com.aradrotem.spendwise.data.notifications.NotificationPreferencesStore
import com.aradrotem.spendwise.data.notifications.NotificationSender
import com.aradrotem.spendwise.data.repository.GroupCloudRepository
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.GroupExpenseReconciler
import com.aradrotem.spendwise.domain.ReconciledExpense
import com.aradrotem.spendwise.domain.RemoteGroupExpense
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

// Orchestrates real cross-account shared-group synchronization - the piece that was previously
// missing (GroupExpenseReconciler existed and was unit-tested, but nothing ever called it). Pulls
// membership/members/expenses from Firestore into local Room via GroupExpenseRepository, and
// pushes locally-created expenses/pending deletions back up. Deliberately holds no Firestore
// reference itself - all cloud I/O goes through GroupCloudRepository, all local I/O through
// GroupExpenseRepository, so this class is pure orchestration.
//
// Called from the same trigger points Step 17's personal SyncEngine already uses
// (SpendWiseApplication.triggerSync - app resume, connectivity regained, manual "Sync now") plus
// two Step 19-specific ones: right after accepting an invitation, and on Groups/Group-Details
// screen entry.
class SharedGroupSyncEngine(
    private val groupExpenseRepository: GroupExpenseRepository,
    private val groupCloudRepository: GroupCloudRepository?,
    private val authRepository: AuthRepository,
    private val notificationSender: NotificationSender? = null,
    private val notificationPreferencesStore: NotificationPreferencesStore? = null,
    private val diagnostics: SyncDiagnostics? = null
) {
    // Single-flight: a resume + a manual "Sync now" firing close together must not run two
    // overlapping full passes (which would double Firestore reads and could interleave writes).
    // A second caller simply waits for the in-flight pass to finish rather than starting its own.
    private val mutex = Mutex()

    // Every group the current account belongs to, pulled from
    // users/{uid}/groupMemberships and reconciled one by one. One broken group (malformed remote
    // data, a transient permission error) is caught and skipped rather than aborting the whole
    // pass - see syncGroup's own try/catch.
    suspend fun syncAll() {
        val cloud = groupCloudRepository ?: run {
            diagnostics?.log("syncAll", "skipped: groupCloudRepository unavailable (no Firebase/legacy account)")
            return
        }
        val uid = authRepository.currentUid ?: run {
            diagnostics?.log("syncAll", "skipped: no signed-in uid")
            return
        }
        mutex.withLock {
            val membershipsResult = cloud.getMyMemberships(uid)
            val memberships = membershipsResult.getOrNull()
            if (memberships == null) {
                diagnostics?.log("syncAll", "getMyMemberships failed: ${membershipsResult.exceptionOrNull()?.message}")
                return@withLock
            }
            diagnostics?.log("syncAll", "discovered ${memberships.size} membership(s) for uid=$uid")
            memberships.forEach { membership ->
                runCatching { syncGroupInternal(membership.groupId, membership.groupName, uid, cloud) }
                    .onFailure { diagnostics?.log("syncAll", "group ${membership.groupId} sync threw: ${it.message}") }
            }
        }
    }

    // Syncs one specific group by its cloud id - used right after accepting an invitation (so the
    // newly-joined group's members/expenses appear immediately without waiting for the next full
    // syncAll) and by Group Details screen entry (refresh just this group instead of every group).
    suspend fun syncGroup(groupSyncId: String, groupName: String) {
        val cloud = groupCloudRepository ?: return
        val uid = authRepository.currentUid ?: return
        mutex.withLock {
            runCatching { syncGroupInternal(groupSyncId, groupName, uid, cloud) }
                .onFailure { diagnostics?.log("syncGroup", "group $groupSyncId sync threw: ${it.message}") }
        }
    }

    private suspend fun syncGroupInternal(groupSyncId: String, groupName: String, uid: String, cloud: GroupCloudRepository) {
        val localGroupId = groupExpenseRepository.getOrCreateLocalGroupForSync(groupSyncId, groupName)
        diagnostics?.log("syncGroup", "local group upserted: groupSyncId=$groupSyncId localId=$localGroupId")

        // Members first - expenses reference members by uid, so the uid->local-id map must be
        // complete (as complete as the cloud member list allows) before expenses are reconciled.
        val membersResult = cloud.getMembersOnce(groupSyncId)
        val remoteMembers = membersResult.getOrNull()
        if (remoteMembers == null) {
            diagnostics?.log("syncGroup", "getMembersOnce($groupSyncId) failed: ${membersResult.exceptionOrNull()?.message}")
            return
        }
        diagnostics?.log("syncGroup", "read ${remoteMembers.size} member(s) for group $groupSyncId")
        remoteMembers.forEach { member ->
            groupExpenseRepository.upsertSyncedMember(localGroupId, member.uid, member.displayName, member.role)
        }

        pushPendingDeletions(groupSyncId, cloud)
        pushPendingExpenses(localGroupId, groupSyncId, uid, cloud)

        val expensesResult = cloud.getExpensesOnce(groupSyncId)
        val remoteExpenses = expensesResult.getOrNull()
        if (remoteExpenses == null) {
            diagnostics?.log("syncGroup", "getExpensesOnce($groupSyncId) failed: ${expensesResult.exceptionOrNull()?.message}")
            return
        }
        val localExpensesByCloudId = groupExpenseRepository.getExpensesByCloudId(localGroupId)
        val memberUidToLocalId = groupExpenseRepository.getMemberUidMap(localGroupId)

        val previouslyKnownCloudIds = localExpensesByCloudId.keys
        val reconciled = GroupExpenseReconciler.reconcile(localGroupId, remoteExpenses, localExpensesByCloudId, memberUidToLocalId)
        groupExpenseRepository.applyReconciledExpenses(reconciled)

        val toDelete = GroupExpenseReconciler.localIdsToDelete(localExpensesByCloudId, remoteExpenses.map { it.cloudId }.toSet())
        groupExpenseRepository.applyRemoteDeletions(toDelete)

        notifyAboutNewRemoteExpenses(reconciled, previouslyKnownCloudIds, uid, groupName)
        diagnostics?.log("syncGroup", "completed: group=$groupSyncId reconciled=${reconciled.size} deleted=${toDelete.size}")
    }

    // Uploads locally-created-or-edited shared-group expenses (see
    // GroupExpenseRepository.getPendingPushExpenses - dirty-flag based, so this covers both a
    // brand-new expense and an edit to an already-synced one in the same pass). A brand-new
    // expense gets a client-generated cloud document id (UUID) so the local row can be stamped
    // with its final cloudId atomically with the push; an edit reuses the expense's existing
    // cloudId so the push overwrites the same cloud document rather than creating a duplicate.
    // createdByUid is likewise preserved across edits (never reassigned to whoever happened to
    // trigger the re-push) so attribution never silently changes.
    private suspend fun pushPendingExpenses(localGroupId: Long, groupSyncId: String, uid: String, cloud: GroupCloudRepository) {
        val pending = groupExpenseRepository.getPendingPushExpenses(localGroupId)
        if (pending.isEmpty()) return
        val localIdToUid = groupExpenseRepository.getMemberLocalIdToUidMap(localGroupId)

        pending.forEach { expense ->
            val payerUid = localIdToUid[expense.paidByMemberId] ?: return@forEach
            val localShares = groupExpenseRepository.getShares(expense.id)
            val remoteShares = localShares.mapNotNull { share -> localIdToUid[share.memberId]?.let { it to share.shareAmountCents } }
            // Every participant must be an authenticated member with a known uid - a share
            // referencing a still-local-only (memberUid == null) person can't be represented in
            // the shared cloud expense shape, so this expense is skipped until that's resolved.
            if (remoteShares.size != localShares.size) return@forEach

            val cloudId = expense.cloudId ?: UUID.randomUUID().toString()
            val creatorUid = expense.createdByUid ?: uid
            val remote = RemoteGroupExpense(
                cloudId = cloudId,
                title = expense.title,
                amountCents = expense.amountCents,
                dateEpochDay = expense.dateEpochDay,
                paidByUid = payerUid,
                splitMethod = expense.splitMethod,
                note = expense.note,
                createdAtEpochMillis = expense.createdAtEpochMillis,
                createdByUid = creatorUid,
                shares = remoteShares.toMap()
            )
            cloud.pushExpense(groupSyncId, remote).onSuccess {
                groupExpenseRepository.markExpensePushed(expense.id, cloudId, creatorUid)
            }
        }
    }

    // Drains the durable queue of shared expenses deleted locally that still need their cloud
    // counterpart removed (see GroupExpenseRepository.deleteExpense) - retried here so a delete
    // made while offline eventually reaches Firestore instead of leaving a ghost expense visible
    // to other members forever.
    private suspend fun pushPendingDeletions(groupSyncId: String, cloud: GroupCloudRepository) {
        groupExpenseRepository.getPendingDeletions()
            .filter { it.groupSyncId == groupSyncId }
            .forEach { pending ->
                cloud.deleteExpense(groupSyncId, pending.cloudId).onSuccess {
                    groupExpenseRepository.clearPendingDeletion(pending)
                }
            }
    }

    // Fires one "shared-group notifications" alert per genuinely new remote expense - an expense
    // whose cloudId wasn't already present locally before this pass, and which the current user
    // did not create themselves (never notify the creator about their own upload). Silently no-ops
    // if the preference is off or the sender has no notification permission - the sync itself must
    // never fail because of a notification problem.
    private fun notifyAboutNewRemoteExpenses(
        reconciled: List<ReconciledExpense>,
        previouslyKnownCloudIds: Set<String>,
        currentUid: String,
        groupName: String
    ) {
        val sender = notificationSender ?: return
        if (notificationPreferencesStore?.sharedGroupNotificationsEnabled?.value != true) return

        reconciled
            .filter { it.cloudId !in previouslyKnownCloudIds && it.entity.createdByUid != currentUid }
            .forEach { newExpense ->
                sender.send(
                    NotificationChannels.SHARED_GROUPS,
                    ("group_expense_${newExpense.cloudId}").hashCode(),
                    "New shared expense",
                    "${newExpense.entity.title} was added to \"$groupName\"."
                )
            }
    }
}
