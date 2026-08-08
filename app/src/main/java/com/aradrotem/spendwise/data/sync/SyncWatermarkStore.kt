package com.aradrotem.spendwise.data.sync

import android.content.Context

// Per-(account, entity-type) pull watermark: the highest Firestore server-write time already
// pulled. Persisted so a process restart resumes pulling only newer changes instead of re-reading
// everything.
interface SyncWatermarkStore {
    suspend fun getWatermark(type: SyncEntityType): Long
    suspend fun setWatermark(type: SyncEntityType, value: Long)
}

class SharedPrefsSyncWatermarkStore(context: Context, uid: String) : SyncWatermarkStore {
    private val prefs = context.getSharedPreferences("spendwise_sync_watermarks_$uid", Context.MODE_PRIVATE)

    override suspend fun getWatermark(type: SyncEntityType): Long = prefs.getLong(type.tag, 0L)

    override suspend fun setWatermark(type: SyncEntityType, value: Long) {
        prefs.edit().putLong(type.tag, value).apply()
    }
}
