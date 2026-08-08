package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanDao
import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType

class RecurringPlanSyncAdapter(private val dao: RecurringPaymentPlanDao, uid: String) : EntitySyncAdapter<RecurringPaymentPlanEntity> {

    override val entityType = SyncEntityType.RECURRING_PLAN
    override val collectionPath = "users/$uid/recurringPlans"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val plan = dao.getById(localId) ?: return null
        return mapOf(
            "type" to plan.type.name,
            "title" to plan.title,
            "categoryName" to plan.categoryName,
            "note" to plan.note,
            "amountInCents" to plan.amountInCents,
            "totalAmountInCents" to plan.totalAmountInCents,
            "totalInstallments" to plan.totalInstallments,
            "firstPaymentDateMillis" to plan.firstPaymentDateMillis,
            "preferredDayOfMonth" to plan.preferredDayOfMonth,
            "endDateMillis" to plan.endDateMillis,
            "status" to plan.status.name,
            "createdAt" to plan.createdAt
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val entity = RecurringPaymentPlanEntity(
            id = existingLocalId ?: 0,
            type = RecurringPlanType.valueOf(data["type"] as String),
            title = data["title"] as String,
            categoryName = data["categoryName"] as String,
            note = data["note"] as? String ?: "",
            amountInCents = (data["amountInCents"] as? Number)?.toLong(),
            totalAmountInCents = (data["totalAmountInCents"] as? Number)?.toLong(),
            totalInstallments = (data["totalInstallments"] as? Number)?.toInt(),
            firstPaymentDateMillis = (data["firstPaymentDateMillis"] as Number).toLong(),
            preferredDayOfMonth = (data["preferredDayOfMonth"] as Number).toInt(),
            endDateMillis = (data["endDateMillis"] as? Number)?.toLong(),
            status = RecurringPlanStatus.valueOf(data["status"] as String),
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
        return if (existingLocalId != null) {
            dao.update(entity)
            existingLocalId
        } else {
            dao.insert(entity)
        }
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        dao.getById(localId)?.let { dao.delete(it) }
    }
}
