package com.aradrotem.spendwise.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

// Every write stamps a server-assigned "serverSyncedAt" timestamp field (FieldValue.serverTimestamp)
// alongside the caller's own data. Pulls query and order by that field, not any client-supplied
// updatedAt, so two devices with skewed clocks can never cause a pull to silently miss a change -
// see SyncEngine.
class FirebaseFirestoreSyncClient(private val firestore: FirebaseFirestore) : FirestoreSyncClient {

    override suspend fun upsert(collectionPath: String, docId: String, data: Map<String, Any?>) {
        val withServerTimestamp = data + (SERVER_SYNCED_AT_FIELD to FieldValue.serverTimestamp())
        firestore.collection(collectionPath).document(docId).set(withServerTimestamp).await()
    }

    override suspend fun queryChangedSince(collectionPath: String, sinceServerTimeMillis: Long): List<FirestoreSyncDoc> {
        val sinceTimestamp = Timestamp(sinceServerTimeMillis / 1000, ((sinceServerTimeMillis % 1000) * 1_000_000).toInt())
        val snapshot = firestore.collection(collectionPath)
            .whereGreaterThan(SERVER_SYNCED_AT_FIELD, sinceTimestamp)
            .orderBy(SERVER_SYNCED_AT_FIELD, Query.Direction.ASCENDING)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            val serverTimeMillis = doc.getTimestamp(SERVER_SYNCED_AT_FIELD)?.toDate()?.time ?: 0L
            FirestoreSyncDoc(docId = doc.id, data = doc.data ?: emptyMap(), serverTimeMillis = serverTimeMillis)
        }
    }

    companion object {
        private const val SERVER_SYNCED_AT_FIELD = "serverSyncedAt"
    }
}
