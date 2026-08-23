package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupExpenseDao
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeGroupExpenseDao : GroupExpenseDao() {
    private val expenses = mutableListOf<GroupExpenseEntity>()
    private val shares = mutableListOf<GroupExpenseShareEntity>()
    private var nextExpenseId = 1L
    private var nextShareId = 1L

    override suspend fun insertExpense(expense: GroupExpenseEntity): Long {
        val withId = expense.copy(id = nextExpenseId++)
        expenses += withId
        return withId.id
    }

    override suspend fun updateExpenseRow(expense: GroupExpenseEntity) {
        val index = expenses.indexOfFirst { it.id == expense.id }
        if (index >= 0) expenses[index] = expense
    }

    override suspend fun deleteExpense(expense: GroupExpenseEntity) {
        expenses.removeAll { it.id == expense.id }
        shares.removeAll { it.expenseId == expense.id }
    }

    override suspend fun insertShares(shares: List<GroupExpenseShareEntity>) {
        shares.forEach { this.shares += it.copy(id = nextShareId++) }
    }

    override suspend fun deleteSharesForExpense(expenseId: Long) {
        shares.removeAll { it.expenseId == expenseId }
    }

    override fun observeByGroup(groupId: Long): Flow<List<GroupExpenseEntity>> =
        flowOf(expenses.filter { it.groupId == groupId }.sortedWith(compareByDescending<GroupExpenseEntity> { it.dateEpochDay }.thenByDescending { it.id }))

    override fun observeAll(): Flow<List<GroupExpenseEntity>> = flowOf(expenses.toList())

    override fun observeAllShares(): Flow<List<GroupExpenseShareEntity>> = flowOf(shares.toList())

    override suspend fun getById(id: Long): GroupExpenseEntity? = expenses.firstOrNull { it.id == id }

    override suspend fun getSharesForExpense(expenseId: Long): List<GroupExpenseShareEntity> = shares.filter { it.expenseId == expenseId }

    override suspend fun getShareById(id: Long): GroupExpenseShareEntity? = shares.firstOrNull { it.id == id }

    override fun observeSharesByGroup(groupId: Long): Flow<List<GroupExpenseShareEntity>> {
        val expenseIds = expenses.filter { it.groupId == groupId }.map { it.id }.toSet()
        return flowOf(shares.filter { it.expenseId in expenseIds })
    }

    override suspend fun countExpensesPaidByMember(memberId: Long): Int = expenses.count { it.paidByMemberId == memberId }

    override suspend fun countSharesForMember(memberId: Long): Int = shares.count { it.memberId == memberId }

    override suspend fun getByGroupOnce(groupId: Long): List<GroupExpenseEntity> = expenses.filter { it.groupId == groupId }

    override suspend fun markPushed(id: Long, cloudId: String, createdByUid: String) {
        val index = expenses.indexOfFirst { it.id == id }
        if (index >= 0) expenses[index] = expenses[index].copy(cloudId = cloudId, createdByUid = createdByUid)
    }
}
