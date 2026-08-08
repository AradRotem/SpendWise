package com.aradrotem.spendwise.data.sync

// One adapter per syncable entity type - the only place Firestore<->Room field mapping happens
// for that type (see the concrete Room*SyncAdapter implementations). Deliberately DAO/type
// agnostic here so SyncEngine's push/pull/conflict logic can be unit-tested against a trivial
// fake, with no Room or Firestore involved.
interface EntitySyncAdapter<T> {
    val entityType: SyncEntityType
    val collectionPath: String

    // Reads the current local row as a Firestore-ready map (with any foreign keys already
    // resolved to their syncId), or null if the row no longer exists locally (already deleted by
    // some other path - the caller treats this as "nothing to push, drop the bookkeeping row").
    suspend fun loadForPush(localId: Long): Map<String, Any?>?

    // Applies a remote upsert. If existingLocalId is non-null, that local row is updated in
    // place; otherwise a new local row is inserted. Returns the (possibly newly created) local id.
    suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long

    suspend fun applyRemoteDelete(localId: Long)
}
