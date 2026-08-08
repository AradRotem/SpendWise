package com.aradrotem.spendwise.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity): Long

    @Query("SELECT * FROM sync_metadata WHERE entityType = :entityType AND localId = :localId LIMIT 1")
    suspend fun findByLocalId(entityType: String, localId: Long): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE entityType = :entityType AND pendingSync = 1")
    suspend fun getPendingByType(entityType: String): List<SyncMetadataEntity>

    @Query("DELETE FROM sync_metadata WHERE entityType = :entityType AND localId = :localId")
    suspend fun deleteByLocalId(entityType: String, localId: Long)

    @Query("SELECT COUNT(*) FROM sync_metadata WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    // Marks a row dirty for the sync engine to pick up. Creates the metadata row (with a fresh
    // syncId) on first write, or updates the existing one in place - called by repositories after
    // every local insert/update, and by the sync engine's tombstone step after a local delete.
    @Query(
        "UPDATE sync_metadata SET updatedAt = :updatedAt, isDeleted = :isDeleted, pendingSync = 1 " +
            "WHERE entityType = :entityType AND localId = :localId"
    )
    suspend fun markDirty(entityType: String, localId: Long, updatedAt: Long, isDeleted: Boolean = false): Int

    @Query("UPDATE sync_metadata SET pendingSync = 0 WHERE entityType = :entityType AND localId = :localId")
    suspend fun clearPending(entityType: String, localId: Long)
}
