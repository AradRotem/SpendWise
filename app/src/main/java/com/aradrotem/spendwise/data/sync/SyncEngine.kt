package com.aradrotem.spendwise.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Orchestrates push+pull for every registered entity type against Firestore, using
// sync_metadata as the single source of truth for "what's dirty" and "what's already synced".
//
// Conflict rule: last-write-wins by each row's own local `updatedAt` (bumped on every local
// write/delete - see SyncStamper). A remote change only overwrites a local row if the remote
// change is newer AND the local row isn't currently dirty-and-newer itself - this is also what
// guarantees a tombstoned (deleted) row can never be resurrected by a stale pull: deleting bumps
// updatedAt to "now", so any older remote copy can never win before the delete itself has pushed
// and the local sync_metadata row is removed entirely.
//
// Dedup: syncId (not Room's local id) is the sole cross-device identity. Firestore writes always
// use set() keyed by syncId (never add()), so a retried push is idempotent.
class SyncEngine(
    private val syncMetadataDao: SyncMetadataDao,
    private val adapters: List<EntitySyncAdapter<*>>,
    private val firestoreClient: FirestoreSyncClient,
    private val watermarkStore: SyncWatermarkStore
) {
    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Offline)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    // Safe to call concurrently from multiple trigger points (app resume, connectivity regained,
    // manual "Sync now") - overlapping calls coalesce on the mutex rather than double-running.
    suspend fun sync() {
        mutex.withLock {
            _status.value = SyncStatus.Syncing
            try {
                for (adapter in adapters) pushAdapter(adapter)
                for (adapter in adapters) pullAdapter(adapter)
                _lastSyncedAt.value = System.currentTimeMillis()
                _status.value = SyncStatus.Synced
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _status.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    private suspend fun <T> pushAdapter(adapter: EntitySyncAdapter<T>) {
        val pending = syncMetadataDao.getPendingByType(adapter.entityType.tag)
        for (meta in pending) {
            if (meta.isDeleted) {
                firestoreClient.upsert(
                    adapter.collectionPath, meta.syncId,
                    mapOf(DELETED_FIELD to true, UPDATED_AT_FIELD to meta.updatedAt)
                )
                syncMetadataDao.deleteByLocalId(adapter.entityType.tag, meta.localId)
                continue
            }
            val data = adapter.loadForPush(meta.localId)
            if (data == null) {
                // Row vanished without going through the repository's delete path - nothing left
                // to push; drop the now-orphaned bookkeeping row.
                syncMetadataDao.deleteByLocalId(adapter.entityType.tag, meta.localId)
                continue
            }
            firestoreClient.upsert(adapter.collectionPath, meta.syncId, data + (UPDATED_AT_FIELD to meta.updatedAt))
            syncMetadataDao.clearPending(adapter.entityType.tag, meta.localId)
        }
    }

    private suspend fun <T> pullAdapter(adapter: EntitySyncAdapter<T>) {
        val watermark = watermarkStore.getWatermark(adapter.entityType)
        val changed = firestoreClient.queryChangedSince(adapter.collectionPath, watermark)
        var maxSeen = watermark

        for (doc in changed) {
            if (doc.serverTimeMillis > maxSeen) maxSeen = doc.serverTimeMillis

            val existingMeta = syncMetadataDao.findBySyncId(doc.docId)
            val remoteUpdatedAt = (doc.data[UPDATED_AT_FIELD] as? Number)?.toLong() ?: doc.serverTimeMillis
            val remoteIsDeleted = doc.data[DELETED_FIELD] == true

            // Local dirty-and-newer always wins over a pull - this is the rule that protects a
            // fresh local tombstone from being resurrected by an older remote copy.
            if (existingMeta != null && existingMeta.pendingSync && existingMeta.updatedAt >= remoteUpdatedAt) {
                continue
            }
            if (existingMeta != null && !existingMeta.pendingSync && existingMeta.updatedAt > remoteUpdatedAt) {
                continue
            }

            if (remoteIsDeleted) {
                if (existingMeta != null) {
                    adapter.applyRemoteDelete(existingMeta.localId)
                    syncMetadataDao.deleteByLocalId(adapter.entityType.tag, existingMeta.localId)
                }
                continue
            }

            val localId = adapter.applyRemoteUpsert(doc.docId, doc.data, existingMeta?.localId)
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    entityType = adapter.entityType.tag,
                    localId = localId,
                    syncId = doc.docId,
                    updatedAt = remoteUpdatedAt,
                    isDeleted = false,
                    pendingSync = false
                )
            )
        }

        watermarkStore.setWatermark(adapter.entityType, maxSeen)
    }

    companion object {
        const val UPDATED_AT_FIELD = "updatedAt"
        const val DELETED_FIELD = "deleted"
    }
}
