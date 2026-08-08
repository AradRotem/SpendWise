package com.aradrotem.spendwise.data.sync

// A document changed at-or-after a sync watermark. serverTimeMillis comes from Firestore's own
// server-assigned write time (not any device's clock) - see SyncEngine for why the pull watermark
// is based on this rather than a client-supplied updatedAt, to stay correct even when two
// devices' clocks disagree.
data class FirestoreSyncDoc(
    val docId: String,
    val data: Map<String, Any?>,
    val serverTimeMillis: Long
)

// Thin interface SyncEngine depends on instead of FirebaseFirestore directly, so the engine's own
// push/pull/conflict logic is unit-testable with a fake, no live Firestore involved.
interface FirestoreSyncClient {
    suspend fun upsert(collectionPath: String, docId: String, data: Map<String, Any?>)
    suspend fun queryChangedSince(collectionPath: String, sinceServerTimeMillis: Long): List<FirestoreSyncDoc>
}
