package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.ExpenseGroupDao
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType

class GroupSyncAdapter(private val dao: ExpenseGroupDao, uid: String) : EntitySyncAdapter<ExpenseGroupEntity> {

    override val entityType = SyncEntityType.GROUP
    override val collectionPath = "users/$uid/groups"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val group = dao.getById(localId) ?: return null
        return mapOf("name" to group.name, "createdAtEpochMillis" to group.createdAtEpochMillis)
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val entity = ExpenseGroupEntity(
            id = existingLocalId ?: 0,
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
