package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.ExpenseGroupDao
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.local.GroupExpenseDao
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import com.aradrotem.spendwise.data.local.GroupMemberDao
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import kotlinx.coroutines.flow.Flow

// Wraps the three closely-coupled group-expense DAOs (a group's members, expenses and shares are
// only ever created/edited/deleted together - see the delete-safety and cross-group checks below).
// Kept as a single repository, mirroring how RecurringPaymentRepository owns its plan DAO, rather
// than splitting into one repository per table.
class GroupExpenseRepository(
    private val groupDao: ExpenseGroupDao,
    private val memberDao: GroupMemberDao,
    private val expenseDao: GroupExpenseDao
) {

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
        trimmedMemberNames.forEach { memberName ->
            memberDao.insert(GroupMemberEntity(groupId = groupId, name = memberName))
        }
        return Result.success(groupId)
    }

    suspend fun renameGroup(id: Long, name: String): Result<Unit> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name is required"))
        }
        val existing = groupDao.getById(id) ?: return Result.failure(IllegalStateException("Group not found"))
        groupDao.update(existing.copy(name = trimmedName))
        return Result.success(Unit)
    }

    // Members/expenses/shares all cascade-delete via their foreign keys, so this single call
    // removes the group's entire dataset.
    suspend fun deleteGroup(group: ExpenseGroupEntity) = groupDao.delete(group)

    suspend fun addMember(groupId: Long, name: String): Result<Long> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Member name is required"))
        }
        if (memberDao.countByNormalizedName(groupId, trimmedName) > 0) {
            return Result.failure(IllegalArgumentException("A member with this name already exists in this group"))
        }
        return Result.success(memberDao.insert(GroupMemberEntity(groupId = groupId, name = trimmedName)))
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
        return Result.success(Unit)
    }

    suspend fun deleteExpense(expense: GroupExpenseEntity) = expenseDao.deleteExpense(expense)

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
