package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {

    @Insert
    suspend fun insert(member: GroupMemberEntity): Long

    @Update
    suspend fun update(member: GroupMemberEntity)

    @Delete
    suspend fun delete(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY createdAtEpochMillis ASC")
    fun observeByGroup(groupId: Long): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY createdAtEpochMillis ASC")
    suspend fun getByGroup(groupId: Long): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE id = :id")
    suspend fun getById(id: Long): GroupMemberEntity?

    // Duplicate-name check, ignoring case and surrounding whitespace, scoped to one group.
    // excludeId lets renaming a member skip matching itself.
    @Query(
        "SELECT COUNT(*) FROM group_members WHERE groupId = :groupId " +
            "AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND id != :excludeId"
    )
    suspend fun countByNormalizedName(groupId: Long, name: String, excludeId: Long = 0L): Int
}
