package com.aradrotem.spendwise.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        GroupExpenseShareEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class SpendWiseDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringPaymentPlanDao(): RecurringPaymentPlanDao
    abstract fun recurringOccurrenceExceptionDao(): RecurringOccurrenceExceptionDao
    abstract fun expenseGroupDao(): ExpenseGroupDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun groupExpenseDao(): GroupExpenseDao

    companion object {
        private const val DATABASE_NAME = "spendwise.db"

        @Volatile
        private var instance: SpendWiseDatabase? = null

        fun getInstance(context: Context): SpendWiseDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpendWiseDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
