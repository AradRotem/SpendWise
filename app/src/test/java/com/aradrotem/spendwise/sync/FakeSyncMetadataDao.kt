package com.aradrotem.spendwise.sync

import com.aradrotem.spendwise.data.sync.SyncMetadataDao
import com.aradrotem.spendwise.data.sync.SyncMetadataEntity

class FakeSyncMetadataDao : SyncMetadataDao {
    private val rows = mutableListOf<SyncMetadataEntity>()
    private var nextId = 1L

    override suspend fun upsert(metadata: SyncMetadataEntity): Long {
        val existingIndex = rows.indexOfFirst { it.entityType == metadata.entityType && it.localId == metadata.localId }
        return if (existingIndex >= 0) {
            val id = rows[existingIndex].id
            rows[existingIndex] = metadata.copy(id = id)
            id
        } else {
            val id = nextId++
            rows.add(metadata.copy(id = id))
            id
        }
    }

    override suspend fun findByLocalId(entityType: String, localId: Long): SyncMetadataEntity? =
        rows.firstOrNull { it.entityType == entityType && it.localId == localId }

    override suspend fun findBySyncId(syncId: String): SyncMetadataEntity? =
        rows.firstOrNull { it.syncId == syncId }

    override suspend fun getPendingByType(entityType: String): List<SyncMetadataEntity> =
        rows.filter { it.entityType == entityType && it.pendingSync }

    override suspend fun deleteByLocalId(entityType: String, localId: Long) {
        rows.removeAll { it.entityType == entityType && it.localId == localId }
    }

    override fun observePendingCount() = throw NotImplementedError("not needed for these tests")

    override suspend fun markDirty(entityType: String, localId: Long, updatedAt: Long, isDeleted: Boolean): Int {
        val index = rows.indexOfFirst { it.entityType == entityType && it.localId == localId }
        if (index < 0) return 0
        rows[index] = rows[index].copy(updatedAt = updatedAt, isDeleted = isDeleted, pendingSync = true)
        return 1
    }

    override suspend fun clearPending(entityType: String, localId: Long) {
        val index = rows.indexOfFirst { it.entityType == entityType && it.localId == localId }
        if (index >= 0) rows[index] = rows[index].copy(pendingSync = false)
    }

    fun allRows(): List<SyncMetadataEntity> = rows.toList()
}
