package com.aradrotem.spendwise.sync

import com.aradrotem.spendwise.data.sync.FirestoreSyncClient
import com.aradrotem.spendwise.data.sync.FirestoreSyncDoc

// In-memory Firestore stand-in. Assigns an incrementing "server time" to every upsert, mirroring
// how FieldValue.serverTimestamp() always advances - lets tests exercise watermark-based
// queryChangedSince without a real Firestore instance.
class FakeFirestoreSyncClient : FirestoreSyncClient {
    private val docs = mutableMapOf<String, MutableMap<String, Pair<Map<String, Any?>, Long>>>()
    private var clock = 0L

    var upsertCallCount = 0
        private set

    override suspend fun upsert(collectionPath: String, docId: String, data: Map<String, Any?>) {
        upsertCallCount++
        clock++
        docs.getOrPut(collectionPath) { mutableMapOf() }[docId] = data to clock
    }

    override suspend fun queryChangedSince(collectionPath: String, sinceServerTimeMillis: Long): List<FirestoreSyncDoc> {
        val collection = docs[collectionPath] ?: return emptyList()
        return collection.entries
            .filter { it.value.second > sinceServerTimeMillis }
            .sortedBy { it.value.second }
            .map { (docId, pair) -> FirestoreSyncDoc(docId, pair.first, pair.second) }
    }

    fun docCount(collectionPath: String): Int = docs[collectionPath]?.size ?: 0

    fun rawDoc(collectionPath: String, docId: String): Map<String, Any?>? = docs[collectionPath]?.get(docId)?.first
}
