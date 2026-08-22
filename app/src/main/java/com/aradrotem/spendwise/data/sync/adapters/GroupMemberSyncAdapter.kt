package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.GroupMemberDao
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver

class GroupMemberSyncAdapter(
    private val dao: GroupMemberDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<GroupMemberEntity> {

    override val entityType = SyncEntityType.GROUP_MEMBER
    override val collectionPath = "users/$uid/groupMembers"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val member = dao.getById(localId) ?: return null
        return mapOf(
            "groupSyncId" to resolver.syncIdFor(SyncEntityType.GROUP, member.groupId),
            "name" to member.name,
            "createdAtEpochMillis" to member.createdAtEpochMillis
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long? {
        val groupSyncId = data["groupSyncId"] as? String
        val groupId = resolver.localIdFor(SyncEntityType.GROUP, groupSyncId)
        // Defer rather than throw if the parent group isn't locally available yet - see
        // EntitySyncAdapter.applyRemoteUpsert / RecurringExceptionSyncAdapter for the same pattern.
        if (groupId == null) return null
        val entity = GroupMemberEntity(
            id = existingLocalId ?: 0,
            groupId = groupId,
            name = data["name"] as String,
            createdAtEpochMillis = (data["createdAtEpochMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
        return if (existingLocalId != null) {
            dao.update(entity)
            existingLocalId
        } else {
            dao.insert(entity)
        }
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        dao.getById(localId)?.let { dao.delete(it) }
    }
}
