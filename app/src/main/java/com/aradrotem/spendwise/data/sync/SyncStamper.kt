package com.aradrotem.spendwise.data.sync

import java.util.UUID

// Centralizes the "mark this local row dirty for sync" logic used by every repository, instead of
// duplicating it six times. syncMetadataDao is nullable and defaults to null in every repository
// constructor specifically so none of the ~existing unit tests that construct a repository
// directly with a fake DAO need to change - only the production composition root
// (RepositoryBundle) supplies a real one. When null, marking is a no-op: sync simply doesn't
// track that repository's writes, which is correct for a repository used outside an account
// context (there is none today, but keeps the contract honest either way).
class SyncStamper(
    private val syncMetadataDao: SyncMetadataDao?,
    private val entityType: SyncEntityType
) {
    suspend fun markDirty(localId: Long) {
        val dao = syncMetadataDao ?: return
        val existing = dao.findByLocalId(entityType.tag, localId)
        if (existing == null) {
            dao.upsert(
                SyncMetadataEntity(
                    entityType = entityType.tag,
                    localId = localId,
                    syncId = UUID.randomUUID().toString(),
                    updatedAt = System.currentTimeMillis(),
                    pendingSync = true
                )
            )
        } else {
            dao.markDirty(entityType.tag, localId, System.currentTimeMillis())
        }
    }

    // Tombstones the sync_metadata row rather than removing it - the sync engine still needs to
    // find it to push the deletion to Firestore before the bookkeeping row itself is removed.
    suspend fun markDeleted(localId: Long) {
        syncMetadataDao?.markDirty(entityType.tag, localId, System.currentTimeMillis(), isDeleted = true)
    }
}
