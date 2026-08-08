package com.aradrotem.spendwise.data.sync

// Resolves between a Room row's local (per-device) Long id and its cross-device syncId, for
// entities that reference another synced entity (e.g. a transaction's recurringPlanId). Firestore
// documents never store a local id - only syncId - so every relationship is translated through
// here in both directions. If the referenced parent hasn't been pulled onto this device yet,
// resolution returns null; callers treat that as "can't apply this row yet" (see the Room*
// adapters) rather than silently writing a wrong/zero id.
class SyncIdResolver(private val syncMetadataDao: SyncMetadataDao) {

    suspend fun syncIdFor(type: SyncEntityType, localId: Long?): String? {
        val id = localId ?: return null
        return syncMetadataDao.findByLocalId(type.tag, id)?.syncId
    }

    suspend fun localIdFor(type: SyncEntityType, syncId: String?): Long? {
        val id = syncId ?: return null
        val meta = syncMetadataDao.findBySyncId(id) ?: return null
        return meta.localId.takeIf { meta.entityType == type.tag }
    }
}
