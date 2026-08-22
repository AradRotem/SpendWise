package com.aradrotem.spendwise.sync

import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType

// A minimal in-memory "entity" backing store, exercised only through the EntitySyncAdapter
// contract - simulates one Room table well enough to test SyncEngine's push/pull/conflict logic
// without any real database.
class FakeEntitySyncAdapter(
    override val entityType: SyncEntityType = SyncEntityType.TRANSACTION,
    override val collectionPath: String = "users/test-uid/transactions"
) : EntitySyncAdapter<Map<String, Any?>> {

    private val rows = mutableMapOf<Long, Map<String, Any?>>()
    private var nextId = 1L
    var deleteCallCount = 0
        private set
    var applyUpsertCallCount = 0
        private set

    // syncIds in this set are deferred (applyRemoteUpsert returns null) instead of applied -
    // simulates a row whose parent isn't locally available yet, for SyncEngine deferral tests.
    private val deferredSyncIds = mutableSetOf<String>()

    fun putLocal(localId: Long, data: Map<String, Any?>) {
        rows[localId] = data
        if (localId >= nextId) nextId = localId + 1
    }

    fun localRow(localId: Long): Map<String, Any?>? = rows[localId]
    fun allLocalIds(): Set<Long> = rows.keys

    fun deferSyncId(syncId: String) {
        deferredSyncIds += syncId
    }

    fun stopDeferring(syncId: String) {
        deferredSyncIds -= syncId
    }

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? = rows[localId]

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long? {
        applyUpsertCallCount++
        if (syncId in deferredSyncIds) return null
        val localId = existingLocalId ?: nextId++
        rows[localId] = data
        return localId
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        deleteCallCount++
        rows.remove(localId)
    }
}
