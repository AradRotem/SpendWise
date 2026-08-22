package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupMemberDao
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeGroupMemberDao : GroupMemberDao {
    private val rows = mutableListOf<GroupMemberEntity>()
    private var nextId = 1L

    override suspend fun insert(member: GroupMemberEntity): Long {
        val withId = member.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun update(member: GroupMemberEntity) {
        val index = rows.indexOfFirst { it.id == member.id }
        if (index >= 0) rows[index] = member
    }

    override suspend fun delete(member: GroupMemberEntity) {
        rows.removeAll { it.id == member.id }
    }

    override fun observeAll(): Flow<List<GroupMemberEntity>> = flowOf(rows.toList())

    override fun observeByGroup(groupId: Long): Flow<List<GroupMemberEntity>> = flowOf(rows.filter { it.groupId == groupId })

    override suspend fun getByGroup(groupId: Long): List<GroupMemberEntity> = rows.filter { it.groupId == groupId }

    override suspend fun getById(id: Long): GroupMemberEntity? = rows.firstOrNull { it.id == id }

    override suspend fun countByNormalizedName(groupId: Long, name: String, excludeId: Long): Int =
        rows.count { it.groupId == groupId && it.name.trim().lowercase() == name.trim().lowercase() && it.id != excludeId }
}
