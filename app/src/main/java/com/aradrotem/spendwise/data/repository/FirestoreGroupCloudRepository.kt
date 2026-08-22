package com.aradrotem.spendwise.data.repository

import android.util.Log
import com.aradrotem.spendwise.data.local.GroupRole
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.domain.GroupInvitation
import com.aradrotem.spendwise.domain.GroupInvitationStatus
import com.aradrotem.spendwise.domain.RemoteGroupExpense
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "GroupCloudRepository"

// Live implementation of GroupCloudRepository - see that interface for why this is the only
// class in the shared-group feature that touches the Firebase SDK directly. Firestore schema
// (documented in full in firestore.rules):
//
//   groups/{groupId}                                 - name, ownerUid, createdAt
//   groups/{groupId}/members/{uid}                    - role, displayName, email, joinedAt
//   groups/{groupId}/expenses/{cloudId}                - title, amountCents, dateEpochDay,
//                                                        paidByUid, splitMethod, note,
//                                                        createdAt, createdByUid, shares (map)
//   groups/{groupId}/invitations/{invitationId}        - inviterUid, inviterEmail, inviteeEmail,
//                                                        groupName, status, createdAt, respondedAt
//   users/{uid}/groupMemberships/{groupId}             - role, groupName, joinedAt (lets a client
//                                                        list "my groups" without a collection-
//                                                        group query across every group)
//
// A group is the single canonical document all members read/write directly - never duplicated
// per-user, unlike users/{uid}/... personal data (see Step 17's sync schema).
class FirestoreGroupCloudRepository(private val firestore: FirebaseFirestore) : GroupCloudRepository {

    private fun groupDoc(groupId: String) = firestore.collection("groups").document(groupId)
    private fun membersCollection(groupId: String) = groupDoc(groupId).collection("members")
    private fun expensesCollection(groupId: String) = groupDoc(groupId).collection("expenses")
    private fun invitationsCollection(groupId: String) = groupDoc(groupId).collection("invitations")
    private fun membershipDoc(uid: String, groupId: String) = firestore.collection("users").document(uid).collection("groupMemberships").document(groupId)
    private fun membershipsCollection(uid: String) = firestore.collection("users").document(uid).collection("groupMemberships")

    // Deliberately two SEPARATE awaited writes, not one batch: Firestore security rules evaluate
    // every write in a single batch against the database state from BEFORE the batch, so a rule
    // on the member doc could never see the group doc "existing" if both were written together.
    // Creating the group doc first (and awaiting it) lets the second write's rule safely call
    // get() on the now-genuinely-committed group doc - see firestore.rules' members/{uid} create
    // rule.
    override suspend fun createSharedGroup(groupName: String, ownerUid: String, ownerDisplayName: String, ownerEmail: String): Result<String> = runCatching {
        val groupRef = firestore.collection("groups").document()
        val groupId = groupRef.id
        groupRef.set(mapOf("name" to groupName, "ownerUid" to ownerUid, "createdAt" to FieldValue.serverTimestamp())).await()

        val batch = firestore.batch()
        batch.set(
            groupRef.collection("members").document(ownerUid),
            mapOf("role" to GroupRole.OWNER.name, "displayName" to ownerDisplayName, "email" to ownerEmail, "joinedAt" to FieldValue.serverTimestamp())
        )
        batch.set(
            membershipDoc(ownerUid, groupId),
            mapOf("role" to GroupRole.OWNER.name, "groupName" to groupName, "joinedAt" to FieldValue.serverTimestamp())
        )
        batch.commit().await()
        groupId
    }.onFailure { Log.w(TAG, "createSharedGroup failed", it) }

    override suspend fun sendInvitation(groupId: String, groupName: String, inviterUid: String, inviterEmail: String, inviteeEmail: String): Result<Unit> = runCatching {
        val invitationRef = invitationsCollection(groupId).document()
        invitationRef.set(
            mapOf(
                "groupName" to groupName,
                "inviterUid" to inviterUid,
                "inviterEmail" to inviterEmail,
                "inviteeEmail" to inviteeEmail.trim().lowercase(),
                "status" to GroupInvitationStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "respondedAt" to null
            )
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "sendInvitation failed", it) }

    // Collection-group query across every group's invitations subcollection, restricted by
    // Firestore security rules to only the documents whose inviteeEmail matches the caller's own
    // authenticated email - see firestore.rules. This is what lets a user see invitations
    // addressed to them without a Cloud Function doing an email->uid lookup.
    override fun observeIncomingInvitations(email: String): Flow<List<GroupInvitation>> = callbackFlow {
        val normalizedEmail = email.trim().lowercase()
        Log.d(TAG, "observeIncomingInvitations: querying for normalized email")
        val registration = firestore.collectionGroup("invitations")
            .whereEqualTo("inviteeEmail", normalizedEmail)
            .whereEqualTo("status", GroupInvitationStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                // A permission-denied result from Firestore rules on a LIST query never throws
                // here - the SDK simply omits any document the caller isn't authorized to read
                // from the results (unlike a get() on one specific doc, which does throw). So an
                // empty result set here can mean "genuinely no pending invitations" OR "a rule
                // mismatch is silently excluding real ones" (see firestore.rules' .lower() note) -
                // this log is the only signal that distinguishes a real listener error from that.
                if (error != null) {
                    Log.w(TAG, "observeIncomingInvitations listener error: ${error.code}", error)
                }
                val count = snapshot?.documents?.size ?: 0
                Log.d(TAG, "observeIncomingInvitations: snapshot has $count invitation doc(s)")
                trySend(snapshot?.documents?.mapNotNull { it.toInvitation() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    override fun observeSentInvitations(groupId: String): Flow<List<GroupInvitation>> = callbackFlow {
        val registration = invitationsCollection(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) Log.w(TAG, "observeSentInvitations listener error: ${error.code}", error)
            trySend(snapshot?.documents?.mapNotNull { it.toInvitation() }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

    // Same non-atomic-batch reasoning as createSharedGroup: the invitation is marked ACCEPTED
    // first and awaited, so the member-doc write's rule can then safely get() that invitation
    // (referenced by acceptedViaInvitationId) and see it already ACCEPTED with a matching
    // inviteeEmail - see firestore.rules.
    override suspend fun acceptInvitation(invitation: GroupInvitation, uid: String, displayName: String, email: String): Result<Unit> = runCatching {
        Log.d(TAG, "acceptInvitation: marking invitation ${invitation.id} ACCEPTED")
        invitationsCollection(invitation.groupId).document(invitation.id)
            .update(mapOf("status" to GroupInvitationStatus.ACCEPTED.name, "respondedAt" to FieldValue.serverTimestamp()))
            .await()

        val batch = firestore.batch()
        batch.set(
            membersCollection(invitation.groupId).document(uid),
            mapOf(
                "role" to GroupRole.MEMBER.name, "displayName" to displayName, "email" to email,
                "joinedAt" to FieldValue.serverTimestamp(), "acceptedViaInvitationId" to invitation.id
            )
        )
        batch.set(
            membershipDoc(uid, invitation.groupId),
            mapOf("role" to GroupRole.MEMBER.name, "groupName" to invitation.groupName, "joinedAt" to FieldValue.serverTimestamp())
        )
        batch.commit().await()
        Log.d(TAG, "acceptInvitation: member + membership docs committed for group ${invitation.groupId}")
        Unit
    }.onFailure { Log.w(TAG, "acceptInvitation failed for invitation ${invitation.id}", it) }

    override suspend fun declineInvitation(invitation: GroupInvitation): Result<Unit> = runCatching {
        invitationsCollection(invitation.groupId).document(invitation.id)
            .update(mapOf("status" to GroupInvitationStatus.DECLINED.name, "respondedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override fun observeMembers(groupId: String): Flow<List<GroupCloudMember>> = callbackFlow {
        val registration = membersCollection(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) Log.w(TAG, "observeMembers listener error: ${error.code}", error)
            val members = snapshot?.documents?.mapNotNull { doc ->
                val role = doc.getString("role")?.let { runCatching { GroupRole.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
                GroupCloudMember(
                    uid = doc.id,
                    displayName = doc.getString("displayName") ?: "",
                    email = doc.getString("email") ?: "",
                    role = role,
                    joinedAtEpochMillis = doc.getTimestamp("joinedAt")?.toDate()?.time ?: 0L
                )
            }.orEmpty()
            trySend(members)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun removeMember(groupId: String, uid: String): Result<Unit> = runCatching {
        val batch = firestore.batch()
        batch.delete(membersCollection(groupId).document(uid))
        batch.delete(membershipDoc(uid, groupId))
        batch.commit().await()
    }

    override suspend fun pushExpense(groupId: String, expense: RemoteGroupExpense): Result<Unit> = runCatching {
        expensesCollection(groupId).document(expense.cloudId).set(
            mapOf(
                "title" to expense.title,
                "amountCents" to expense.amountCents,
                "dateEpochDay" to expense.dateEpochDay,
                "paidByUid" to expense.paidByUid,
                "splitMethod" to expense.splitMethod.name,
                "note" to expense.note,
                "createdAt" to expense.createdAtEpochMillis,
                "createdByUid" to expense.createdByUid,
                "shares" to expense.shares
            )
        ).await()
    }

    override suspend fun deleteExpense(groupId: String, cloudId: String): Result<Unit> = runCatching {
        expensesCollection(groupId).document(cloudId).delete().await()
    }

    override fun observeExpenses(groupId: String): Flow<List<RemoteGroupExpense>> = callbackFlow {
        val registration = expensesCollection(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) Log.w(TAG, "observeExpenses listener error: ${error.code}", error)
            val expenses = snapshot?.documents?.mapNotNull { it.toRemoteExpense() }.orEmpty()
            trySend(expenses)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getMyMemberships(uid: String): Result<List<GroupMembershipRef>> = runCatching {
        val result = membershipsCollection(uid).get().await()
        Log.d(TAG, "getMyMemberships: found ${result.documents.size} membership doc(s)")
        result.documents.mapNotNull { doc ->
            val role = doc.getString("role")?.let { runCatching { GroupRole.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            GroupMembershipRef(groupId = doc.id, groupName = doc.getString("groupName") ?: "", role = role)
        }
    }.onFailure { Log.w(TAG, "getMyMemberships failed", it) }

    override suspend fun getMembersOnce(groupId: String): Result<List<GroupCloudMember>> = runCatching {
        val result = membersCollection(groupId).get().await()
        Log.d(TAG, "getMembersOnce($groupId): found ${result.documents.size} member doc(s)")
        result.documents.mapNotNull { doc ->
            val role = doc.getString("role")?.let { runCatching { GroupRole.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            GroupCloudMember(
                uid = doc.id,
                displayName = doc.getString("displayName") ?: "",
                email = doc.getString("email") ?: "",
                role = role,
                joinedAtEpochMillis = doc.getTimestamp("joinedAt")?.toDate()?.time ?: 0L
            )
        }
    }.onFailure { Log.w(TAG, "getMembersOnce($groupId) failed", it) }

    override suspend fun getExpensesOnce(groupId: String): Result<List<RemoteGroupExpense>> = runCatching {
        expensesCollection(groupId).get().await().documents.mapNotNull { it.toRemoteExpense() }
    }.onFailure { Log.w(TAG, "getExpensesOnce($groupId) failed", it) }

    private fun DocumentSnapshot.toInvitation(): GroupInvitation? {
        if (!exists()) return null
        val status = getString("status")?.let { runCatching { GroupInvitationStatus.valueOf(it) }.getOrNull() } ?: return null
        // A collection-group query document's own parent-of-parent is its owning group.
        val groupId = reference.parent.parent?.id ?: return null
        return GroupInvitation(
            id = id,
            groupId = groupId,
            groupName = getString("groupName") ?: "",
            inviterUid = getString("inviterUid") ?: "",
            inviterEmail = getString("inviterEmail") ?: "",
            inviteeEmail = getString("inviteeEmail") ?: "",
            status = status,
            createdAtEpochMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            respondedAtEpochMillis = getTimestamp("respondedAt")?.toDate()?.time
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toRemoteExpense(): RemoteGroupExpense? {
        if (!exists()) return null
        val splitMethod = getString("splitMethod")?.let { runCatching { GroupSplitMethod.valueOf(it) }.getOrNull() } ?: return null
        val rawShares = get("shares") as? Map<String, Any?> ?: emptyMap()
        return RemoteGroupExpense(
            cloudId = id,
            title = getString("title") ?: "",
            amountCents = getLong("amountCents") ?: 0L,
            dateEpochDay = getLong("dateEpochDay") ?: 0L,
            paidByUid = getString("paidByUid") ?: return null,
            splitMethod = splitMethod,
            note = getString("note") ?: "",
            createdAtEpochMillis = getLong("createdAt") ?: 0L,
            createdByUid = getString("createdByUid") ?: "",
            shares = rawShares.mapNotNull { (uid, cents) -> (cents as? Number)?.toLong()?.let { uid to it } }.toMap()
        )
    }
}
