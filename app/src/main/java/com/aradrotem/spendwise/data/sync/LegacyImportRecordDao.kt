package com.aradrotem.spendwise.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LegacyImportRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: LegacyImportRecordEntity): Long

    @Query(
        "SELECT * FROM legacy_import_map WHERE entityType = :entityType AND legacyLocalId = :legacyLocalId LIMIT 1"
    )
    suspend fun find(entityType: String, legacyLocalId: Long): LegacyImportRecordEntity?

    @Query("SELECT * FROM legacy_import_map WHERE entityType = :entityType")
    suspend fun getAllForType(entityType: String): List<LegacyImportRecordEntity>
}
