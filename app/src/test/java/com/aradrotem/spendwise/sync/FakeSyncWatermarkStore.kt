package com.aradrotem.spendwise.sync

import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncWatermarkStore

class FakeSyncWatermarkStore : SyncWatermarkStore {
    private val watermarks = mutableMapOf<SyncEntityType, Long>()

    override suspend fun getWatermark(type: SyncEntityType): Long = watermarks[type] ?: 0L

    override suspend fun setWatermark(type: SyncEntityType, value: Long) {
        watermarks[type] = value
    }
}
