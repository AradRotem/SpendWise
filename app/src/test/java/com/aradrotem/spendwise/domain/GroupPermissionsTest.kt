package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPermissionsTest {

    @Test
    fun owner_canEditManageAndInvite() {
        assertTrue(GroupPermissions.canEditGroup(GroupRole.OWNER))
        assertTrue(GroupPermissions.canManageGroup(GroupRole.OWNER))
        assertTrue(GroupPermissions.canInviteMembers(GroupRole.OWNER))
    }

    @Test
    fun member_cannotEditManageOrInvite() {
        assertFalse(GroupPermissions.canEditGroup(GroupRole.MEMBER))
        assertFalse(GroupPermissions.canManageGroup(GroupRole.MEMBER))
        assertFalse(GroupPermissions.canInviteMembers(GroupRole.MEMBER))
    }

    @Test
    fun owner_canRemoveOtherMembersButNotSelf() {
        assertTrue(GroupPermissions.canRemoveMember(GroupRole.OWNER, isTargetSelf = false))
        assertFalse(GroupPermissions.canRemoveMember(GroupRole.OWNER, isTargetSelf = true))
    }

    @Test
    fun member_cannotRemoveAnyone() {
        assertFalse(GroupPermissions.canRemoveMember(GroupRole.MEMBER, isTargetSelf = false))
        assertFalse(GroupPermissions.canRemoveMember(GroupRole.MEMBER, isTargetSelf = true))
    }

    @Test
    fun bothRoles_canAddExpenses() {
        assertTrue(GroupPermissions.canAddExpense(GroupRole.OWNER))
        assertTrue(GroupPermissions.canAddExpense(GroupRole.MEMBER))
    }

    @Test
    fun owner_canEditAnyExpense() {
        assertTrue(GroupPermissions.canEditExpense(GroupRole.OWNER, isOwnExpense = false))
        assertTrue(GroupPermissions.canEditExpense(GroupRole.OWNER, isOwnExpense = true))
    }

    @Test
    fun member_canOnlyEditOwnExpense() {
        assertTrue(GroupPermissions.canEditExpense(GroupRole.MEMBER, isOwnExpense = true))
        assertFalse(GroupPermissions.canEditExpense(GroupRole.MEMBER, isOwnExpense = false))
    }
}
