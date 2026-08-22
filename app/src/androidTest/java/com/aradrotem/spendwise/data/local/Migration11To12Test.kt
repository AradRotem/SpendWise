package com.aradrotem.spendwise.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aradrotem.spendwise.data.sync.LegacyImportRecordEntity
import com.aradrotem.spendwise.data.sync.SyncMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Version-11 schema is identical to the real (pre-multi-user-groups) entities - MIGRATION_11_12
// only adds nullable sharing-related columns to expense_groups/group_members/group_expenses - so
// this reuses the real entity/DAO classes directly, same convention as Migration10To11Test.
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringPaymentPlanEntity::class,
        RecurringOccurrenceExceptionEntity::class,
        ExpenseGroupEntity::class,
        GroupMemberEntity::class,
        GroupExpenseEntity::class,
        GroupExpenseShareEntity::class,
        SyncMetadataEntity::class,
        LegacyImportRecordEntity::class,
        ReceiptPendingDeletionEntity::class,
        NotifiedBudgetThresholdEntity::class,
        NotifiedRecurringReminderEntity::class,
        CachedExchangeRateEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class LegacyDatabaseV11 : RoomDatabase() {
    abstract fun expenseGroupDao(): ExpenseGroupDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun groupExpenseDao(): GroupExpenseDao
}

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {

    private val dbName = "migration-11-12-test.db"

    @After
    fun cleanUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrate11To12_preservesExistingLocalGroupAndDefaultsNotShared() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val legacyDb = Room.databaseBuilder(context, LegacyDatabaseV11::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val groupId = legacyDb.expenseGroupDao().insert(ExpenseGroupEntity(name = "Trip"))
        val memberId = legacyDb.groupMemberDao().insert(GroupMemberEntity(groupId = groupId, name = "Ann"))
        legacyDb.groupExpenseDao().insertExpenseWithShares(
            GroupExpenseEntity(groupId = groupId, title = "Dinner", amountCents = 1_000L, dateEpochDay = 0L, paidByMemberId = memberId, splitMethod = GroupSplitMethod.EQUAL),
            listOf(GroupExpenseShareEntity(expenseId = 0, memberId = memberId, shareAmountCents = 1_000L))
        )
        legacyDb.close()

        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()

        // Pre-existing local group/member/expense are fully preserved and default to
        // "not a shared group" - no destructive conversion.
        val group = migratedDb.expenseGroupDao().observeAll().first().single()
        assertEquals("Trip", group.name)
        assertNull(group.groupSyncId)
        assertFalse(group.isSharedGroup)

        val member = migratedDb.groupMemberDao().observeByGroup(groupId).first().single()
        assertNull(member.memberUid)
        assertNull(member.role)

        val expense = migratedDb.groupExpenseDao().observeByGroup(groupId).first().single()
        assertNull(expense.cloudId)

        migratedDb.close()
    }
}
