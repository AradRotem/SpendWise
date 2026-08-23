package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Abstract class (not an interface) so the @Transaction methods below can combine expense and
// share writes atomically within a single Dao, mirroring how tightly the two tables are coupled
// (every expense write also replaces its full set of share rows).
@Dao
abstract class GroupExpenseDao {

    @Insert
    protected abstract suspend fun insertExpense(expense: GroupExpenseEntity): Long

    @Update
    protected abstract suspend fun updateExpenseRow(expense: GroupExpenseEntity)

    @Delete
    abstract suspend fun deleteExpense(expense: GroupExpenseEntity)

    @Insert
    protected abstract suspend fun insertShares(shares: List<GroupExpenseShareEntity>)

    @Query("DELETE FROM group_expense_shares WHERE expenseId = :expenseId")
    protected abstract suspend fun deleteSharesForExpense(expenseId: Long)

    @Query("SELECT * FROM group_expenses WHERE groupId = :groupId ORDER BY dateEpochDay DESC, id DESC")
    abstract fun observeByGroup(groupId: Long): Flow<List<GroupExpenseEntity>>

    // One-shot equivalent of observeByGroup, used by SharedGroupSyncEngine's reconciliation pass
    // (which runs once per sync, not as a live subscription).
    @Query("SELECT * FROM group_expenses WHERE groupId = :groupId")
    abstract suspend fun getByGroupOnce(groupId: Long): List<GroupExpenseEntity>

    @Query("UPDATE group_expenses SET cloudId = :cloudId, createdByUid = :createdByUid WHERE id = :id")
    abstract suspend fun markPushed(id: Long, cloudId: String, createdByUid: String)

    // Every expense across every group - used only by the group list screen to compute each
    // group's summary card (count/total/settled state) without a per-group subscription.
    @Query("SELECT * FROM group_expenses")
    abstract fun observeAll(): Flow<List<GroupExpenseEntity>>

    @Query("SELECT * FROM group_expense_shares")
    abstract fun observeAllShares(): Flow<List<GroupExpenseShareEntity>>

    @Query("SELECT * FROM group_expenses WHERE id = :id")
    abstract suspend fun getById(id: Long): GroupExpenseEntity?

    @Query("SELECT * FROM group_expense_shares WHERE expenseId = :expenseId")
    abstract suspend fun getSharesForExpense(expenseId: Long): List<GroupExpenseShareEntity>

    @Query("SELECT * FROM group_expense_shares WHERE id = :id")
    abstract suspend fun getShareById(id: Long): GroupExpenseShareEntity?

    @Query("SELECT * FROM group_expense_shares WHERE expenseId IN (SELECT id FROM group_expenses WHERE groupId = :groupId)")
    abstract fun observeSharesByGroup(groupId: Long): Flow<List<GroupExpenseShareEntity>>

    @Query("SELECT COUNT(*) FROM group_expenses WHERE paidByMemberId = :memberId")
    abstract suspend fun countExpensesPaidByMember(memberId: Long): Int

    @Query("SELECT COUNT(*) FROM group_expense_shares WHERE memberId = :memberId")
    abstract suspend fun countSharesForMember(memberId: Long): Int

    // Inserts the expense, then its shares stamped with the resulting expenseId - single
    // transaction so a crash mid-way never leaves an expense with partial/missing shares.
    @Transaction
    open suspend fun insertExpenseWithShares(expense: GroupExpenseEntity, shares: List<GroupExpenseShareEntity>): Long {
        val expenseId = insertExpense(expense)
        insertShares(shares.map { it.copy(id = 0, expenseId = expenseId) })
        return expenseId
    }

    // Updates the expense row and fully replaces its shares (delete-then-insert) in one
    // transaction, so editing an expense never leaves stale or partial share rows visible.
    @Transaction
    open suspend fun updateExpenseWithShares(expense: GroupExpenseEntity, shares: List<GroupExpenseShareEntity>) {
        updateExpenseRow(expense)
        deleteSharesForExpense(expense.id)
        insertShares(shares.map { it.copy(id = 0, expenseId = expense.id) })
    }
}
