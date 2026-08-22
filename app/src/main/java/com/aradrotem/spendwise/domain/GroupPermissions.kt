package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupRole

// Deliberately simple two-role model (Step 19 spec explicitly rules out enterprise RBAC):
// OWNER can edit/manage the group, invite members, and remove members; MEMBER can view, add
// expenses, and edit/delete only the expenses they themselves created. Every function here is a
// pure boolean decision so both the UI (hide/disable actions) and Firestore security rules
// (mirrored server-side) can be reasoned about identically - see firestore.rules' matching
// isGroupOwner/canEditExpense conditions.
object GroupPermissions {
    fun canEditGroup(role: GroupRole): Boolean = role == GroupRole.OWNER
    fun canInviteMembers(role: GroupRole): Boolean = role == GroupRole.OWNER
    fun canManageGroup(role: GroupRole): Boolean = role == GroupRole.OWNER

    // The owner may remove any member except themselves (a group must always have an owner);
    // members cannot remove anyone.
    fun canRemoveMember(actingRole: GroupRole, isTargetSelf: Boolean): Boolean =
        actingRole == GroupRole.OWNER && !isTargetSelf

    // Every member (owner or not) can add new expenses.
    fun canAddExpense(role: GroupRole): Boolean = role == GroupRole.OWNER || role == GroupRole.MEMBER

    // The owner can edit/delete any expense in the group; a plain member can only edit/delete an
    // expense they themselves created (createdByUid on GroupExpenseEntity).
    fun canEditExpense(role: GroupRole, isOwnExpense: Boolean): Boolean =
        role == GroupRole.OWNER || (role == GroupRole.MEMBER && isOwnExpense)

    fun canParticipateInSettlement(role: GroupRole): Boolean = role == GroupRole.OWNER || role == GroupRole.MEMBER
}
