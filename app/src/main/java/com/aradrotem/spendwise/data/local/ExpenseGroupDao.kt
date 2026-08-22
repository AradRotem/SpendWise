package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseGroupDao {

    @Insert
    suspend fun insert(group: ExpenseGroupEntity): Long

    @Update
    suspend fun update(group: ExpenseGroupEntity)

    // Members, expenses and shares all cascade-delete via their foreign keys (see
    // GroupMemberEntity/GroupExpenseEntity/GroupExpenseShareEntity), so a single delete here
    // removes the whole group's data in one transaction.
    @Delete
    suspend fun delete(group: ExpenseGroupEntity)

    @Query("SELECT * FROM expense_groups ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ExpenseGroupEntity>>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    suspend fun getById(id: Long): ExpenseGroupEntity?

    // Step 19: the local mirror row for a real shared group, keyed by its canonical cloud
    // identity - used by SharedGroupSyncEngine to find (or know it must create) the local row for
    // a groupSyncId discovered via users/{uid}/groupMemberships.
    @Query("SELECT * FROM expense_groups WHERE groupSyncId = :groupSyncId")
    suspend fun getByGroupSyncId(groupSyncId: String): ExpenseGroupEntity?
}
