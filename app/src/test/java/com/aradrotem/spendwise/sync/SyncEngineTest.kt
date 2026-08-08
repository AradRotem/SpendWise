package com.aradrotem.spendwise.sync

import com.aradrotem.spendwise.data.sync.SyncEngine
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncMetadataEntity
import com.aradrotem.spendwise.data.sync.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    private fun engine(
        metadataDao: FakeSyncMetadataDao = FakeSyncMetadataDao(),
        adapter: FakeEntitySyncAdapter = FakeEntitySyncAdapter(),
        firestore: FakeFirestoreSyncClient = FakeFirestoreSyncClient(),
        watermarks: FakeSyncWatermarkStore = FakeSyncWatermarkStore()
    ) = SyncEngine(metadataDao, listOf(adapter), firestore, watermarks)

    @Test
    fun sync_pushesPendingRowsAndClearsPendingFlag() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        adapter.putLocal(1L, mapOf("note" to "coffee"))
        metadataDao.upsert(SyncMetadataEntity(entityType = SyncEntityType.TRANSACTION.tag, localId = 1L, syncId = "sync-1", updatedAt = 100L, pendingSync = true))

        engine(metadataDao, adapter, firestore).sync()

        assertEquals(1, firestore.docCount(adapter.collectionPath))
        assertEquals("coffee", firestore.rawDoc(adapter.collectionPath, "sync-1")?.get("note"))
        val meta = metadataDao.findBySyncId("sync-1")
        assertFalse(meta!!.pendingSync)
    }

    @Test
    fun sync_pullsNewRemoteRowNotYetKnownLocally() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        firestore.upsert(adapter.collectionPath, "remote-1", mapOf("note" to "from other device", "updatedAt" to 500L))

        engine(metadataDao, adapter, firestore).sync()

        assertEquals(1, adapter.allLocalIds().size)
        val localId = adapter.allLocalIds().first()
        assertEquals("from other device", adapter.localRow(localId)?.get("note"))
        assertEquals("remote-1", metadataDao.findBySyncId("remote-1")?.syncId)
    }

    @Test
    fun sync_lastWriteWins_newerRemoteOverwritesOlderLocal() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        adapter.putLocal(1L, mapOf("note" to "old local"))
        metadataDao.upsert(SyncMetadataEntity(entityType = SyncEntityType.TRANSACTION.tag, localId = 1L, syncId = "sync-1", updatedAt = 100L, pendingSync = false))
        firestore.upsert(adapter.collectionPath, "sync-1", mapOf("note" to "newer remote", "updatedAt" to 999L))

        engine(metadataDao, adapter, firestore).sync()

        assertEquals("newer remote", adapter.localRow(1L)?.get("note"))
    }

    @Test
    fun sync_localDirtyAndNewer_winsOverStaleRemotePull() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        adapter.putLocal(1L, mapOf("note" to "fresh local edit"))
        metadataDao.upsert(SyncMetadataEntity(entityType = SyncEntityType.TRANSACTION.tag, localId = 1L, syncId = "sync-1", updatedAt = 999L, pendingSync = true))
        firestore.upsert(adapter.collectionPath, "sync-1", mapOf("note" to "stale remote", "updatedAt" to 100L))

        engine(metadataDao, adapter, firestore).sync()

        // Local wins: value unchanged, and the fresh local edit still got pushed by this same sync.
        assertEquals("fresh local edit", adapter.localRow(1L)?.get("note"))
    }

    @Test
    fun sync_tombstoneNeverResurrectedByStalePull() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        // Repositories hard-delete the local entity row immediately (see SyncStamper) - only the
        // sync_metadata tombstone survives locally until it's pushed. An older remote copy
        // (updatedAt 100, from before the delete) already exists in Firestore.
        metadataDao.upsert(SyncMetadataEntity(entityType = SyncEntityType.TRANSACTION.tag, localId = 1L, syncId = "sync-1", updatedAt = 999L, isDeleted = true, pendingSync = true))
        firestore.upsert(adapter.collectionPath, "sync-1", mapOf("note" to "stale remote copy", "updatedAt" to 100L))

        engine(metadataDao, adapter, firestore).sync()

        // The tombstone pushes first (removing the local metadata row + writing deleted=true to
        // Firestore); the row must never be (re)created locally by this or any later sync.
        assertNull(adapter.localRow(1L))
        assertNull(metadataDao.findBySyncId("sync-1"))
    }

    @Test
    fun sync_remoteDeleteAppliesLocalHardDelete() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        adapter.putLocal(1L, mapOf("note" to "will be deleted remotely"))
        metadataDao.upsert(SyncMetadataEntity(entityType = SyncEntityType.TRANSACTION.tag, localId = 1L, syncId = "sync-1", updatedAt = 100L, pendingSync = false))
        firestore.upsert(adapter.collectionPath, "sync-1", mapOf("deleted" to true, "updatedAt" to 500L))

        engine(metadataDao, adapter, firestore).sync()

        assertNull(adapter.localRow(1L))
        assertEquals(1, adapter.deleteCallCount)
        assertNull(metadataDao.findBySyncId("sync-1"))
    }

    @Test
    fun sync_repeatedSyncWithNoChanges_doesNotDuplicateOrReupload() = runBlocking {
        val metadataDao = FakeSyncMetadataDao()
        val adapter = FakeEntitySyncAdapter()
        val firestore = FakeFirestoreSyncClient()
        adapter.putLocal(1L, mapOf("note" to "coffee"))
        metadataDao.upsert(SyncMetadataEntity(entityType = SyncEntityType.TRANSACTION.tag, localId = 1L, syncId = "sync-1", updatedAt = 100L, pendingSync = true))
        val engine = engine(metadataDao, adapter, firestore)

        engine.sync()
        val countAfterFirst = firestore.upsertCallCount
        engine.sync()
        engine.sync()

        assertEquals(countAfterFirst, firestore.upsertCallCount) // no re-push once clean
        assertEquals(1, firestore.docCount(adapter.collectionPath)) // no duplicate docs
    }

    @Test
    fun sync_setsSyncedStatusOnSuccess() = runBlocking {
        val engine = engine()
        engine.sync()
        assertTrue(engine.status.value is SyncStatus.Synced)
    }
}
