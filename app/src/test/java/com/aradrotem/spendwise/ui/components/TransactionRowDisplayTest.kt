package com.aradrotem.spendwise.ui.components

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// transactionPrimaryText/transactionSecondaryText are extracted as plain functions (see
// TransactionRow.kt) specifically so this display logic - especially the sourceTitle/category
// fallback that caused the "category shown twice" bug - is directly unit-testable without
// rendering Compose.
class TransactionRowDisplayTest {

    private fun generatedExpense(
        sourceTitle: String?,
        category: String = "ENTERTAINMENT",
        installmentNumber: Int? = null,
        totalInstallments: Int? = null
    ) = TransactionEntity(
        amountInCents = 1_500L,
        type = TransactionType.EXPENSE,
        category = category,
        timestamp = 1_000L,
        recurringPlanId = 1L,
        isAutomaticallyGenerated = true,
        scheduledYearMonth = "2026-03",
        sourceTitle = sourceTitle,
        installmentNumber = installmentNumber,
        totalInstallments = totalInstallments
    )

    @Test
    fun primaryText_prefersNonBlankSourceTitle() {
        val transaction = generatedExpense(sourceTitle = "Netflix")
        assertEquals("Netflix", transactionPrimaryText(transaction))
    }

    @Test
    fun primaryText_fallsBackToCategoryWhenSourceTitleIsNull() {
        val transaction = generatedExpense(sourceTitle = null)
        assertEquals("Entertainment", transactionPrimaryText(transaction))
    }

    @Test
    fun primaryText_fallsBackToCategoryWhenSourceTitleIsBlank() {
        val transaction = generatedExpense(sourceTitle = "   ")
        assertEquals("Entertainment", transactionPrimaryText(transaction))
    }

    @Test
    fun primaryText_forInstallmentTransaction_showsTitleNotCategory() {
        val transaction = generatedExpense(
            sourceTitle = "Laptop", category = "ELECTRONICS", installmentNumber = 1, totalInstallments = 6
        )
        assertEquals("Laptop", transactionPrimaryText(transaction))
    }

    @Test
    fun primaryText_forManualTransaction_showsCategory() {
        val manual = TransactionEntity(
            amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L
        )
        assertEquals("Food", transactionPrimaryText(manual))
    }

    @Test
    fun secondaryText_forRecurringExpense_showsCategoryAndLabel() {
        val transaction = generatedExpense(sourceTitle = "Netflix", category = "ENTERTAINMENT")
        assertEquals("Entertainment · Recurring expense", transactionSecondaryText(transaction))
    }

    @Test
    fun secondaryText_forInstallment_showsCategoryAndProgress() {
        val transaction = generatedExpense(
            sourceTitle = "Laptop", category = "ELECTRONICS", installmentNumber = 1, totalInstallments = 6
        )
        assertEquals("Electronics · Installment 1 of 6", transactionSecondaryText(transaction))
    }

    @Test
    fun secondaryText_forRecurringIncome_showsRecurringIncomeLabel() {
        val salary = TransactionEntity(
            amountInCents = 1_000_000L, type = TransactionType.INCOME, category = "SALARY", timestamp = 1_000L,
            recurringPlanId = 1L, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-03",
            sourceTitle = "Monthly salary"
        )
        assertEquals("Salary · Recurring income", transactionSecondaryText(salary))
    }

    @Test
    fun secondaryText_forManualTransaction_isNull() {
        val manual = TransactionEntity(
            amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L
        )
        assertNull(transactionSecondaryText(manual))
    }

    // --- hasReceipt: must recognize a receipt synced in from another device, not just a local file ---

    private fun manualTransaction(receiptLocalUri: String? = null, receiptStoragePath: String? = null) = TransactionEntity(
        amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L,
        receiptLocalUri = receiptLocalUri, receiptStoragePath = receiptStoragePath
    )

    @Test
    fun hasReceipt_falseWhenNeitherLocalNorRemoteReceiptReferenceIsSet() {
        assertEquals(false, hasReceipt(manualTransaction()))
    }

    @Test
    fun hasReceipt_trueForLocalOnlyReceipt() {
        assertEquals(true, hasReceipt(manualTransaction(receiptLocalUri = "/cache/receipts/r1.jpg")))
    }

    // A receipt pulled in via Firestore sync from another device may have a durable Storage path
    // but no local cache yet - it must still count as "has a receipt" so View receipt/the paperclip
    // indicator appear and the viewer can resolve it via download URL.
    @Test
    fun hasReceipt_trueForRemoteOnlyReceipt_withNoLocalCache() {
        assertEquals(true, hasReceipt(manualTransaction(receiptStoragePath = "users/uid/receipts/r1/receipt.jpg")))
    }

    @Test
    fun hasReceipt_trueWhenBothLocalAndRemoteReferencesArePresent() {
        assertEquals(
            true,
            hasReceipt(manualTransaction(receiptLocalUri = "/cache/receipts/r1.jpg", receiptStoragePath = "users/uid/receipts/r1/receipt.jpg"))
        )
    }
}
