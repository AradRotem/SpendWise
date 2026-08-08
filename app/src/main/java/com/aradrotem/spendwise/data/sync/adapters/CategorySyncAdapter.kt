package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.CategoryDao
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import kotlinx.coroutines.flow.first

// Only custom categories ever get marked dirty (see CategoryRepository/SyncStamper) - built-ins
// are seeded identically and locally on every account, so there's never a built-in row to push,
// and a pulled remote category is always applied as a plain (non-built-in) row.
class CategorySyncAdapter(private val dao: CategoryDao, uid: String) : EntitySyncAdapter<CategoryEntity> {

    override val entityType = SyncEntityType.CATEGORY
    override val collectionPath = "users/$uid/categories"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val category = findById(localId) ?: return null
        return mapOf(
            "name" to category.name,
            "normalizedName" to category.normalizedName,
            "type" to category.type.name
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val type = TransactionType.valueOf(data["type"] as String)
        val normalizedName = data["normalizedName"] as String
        val entity = CategoryEntity(
            id = existingLocalId ?: 0,
            name = data["name"] as String,
            normalizedName = normalizedName,
            type = type,
            isBuiltIn = false
        )
        if (existingLocalId != null) return existingLocalId
        // Categories have no update-by-id DAO method (see CategoryDao) - avoid a duplicate if a
        // category with this normalized name/type already exists locally for any reason.
        val existingByName = dao.findByNormalizedNameAndType(normalizedName, type)
        return existingByName?.id ?: dao.insert(entity)
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        findById(localId)?.let { dao.deleteCustomCategoryAndReassign(it) }
    }

    // CategoryDao has no getById; scan both types' current snapshot instead. Category counts are
    // small (user-managed lists), so this is cheap in practice.
    private suspend fun findById(localId: Long): CategoryEntity? {
        for (type in TransactionType.entries) {
            val list = dao.observeByType(type).first()
            list.firstOrNull { it.id == localId }?.let { return it }
        }
        return null
    }
}
