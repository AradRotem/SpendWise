package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.BudgetDao
import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType

class BudgetSyncAdapter(private val dao: BudgetDao, uid: String) : EntitySyncAdapter<BudgetEntity> {

    override val entityType = SyncEntityType.BUDGET
    override val collectionPath = "users/$uid/budgets"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val budget = dao.getById(localId) ?: return null
        return mapOf("categoryName" to budget.categoryName, "monthlyLimitCents" to budget.monthlyLimitCents)
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val categoryName = data["categoryName"] as String
        val monthlyLimitCents = (data["monthlyLimitCents"] as Number).toLong()
        return if (existingLocalId != null) {
            dao.update(BudgetEntity(id = existingLocalId, categoryName = categoryName, monthlyLimitCents = monthlyLimitCents))
            existingLocalId
        } else {
            val existingByCategory = dao.findByCategory(categoryName)
            if (existingByCategory != null) {
                dao.update(existingByCategory.copy(monthlyLimitCents = monthlyLimitCents))
                existingByCategory.id
            } else {
                dao.insert(BudgetEntity(categoryName = categoryName, monthlyLimitCents = monthlyLimitCents))
            }
        }
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        dao.getById(localId)?.let { dao.delete(it) }
    }
}
