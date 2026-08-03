package com.aradrotem.spendwise.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupExpenseRepositoryTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var repository: GroupExpenseRepository

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GroupExpenseRepository(database.expenseGroupDao(), database.groupMemberDao(), database.groupExpenseDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createGroup_withDuplicateMemberNames_fails() = runBlocking {
        val result = repository.createGroup("Trip", listOf("Ann", " ann "))

        assertTrue(result.isFailure)
    }

    @Test
    fun createGroup_persistsGroupAndMembersTogether() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()

        val members = repository.observeMembers(groupId).first()
        assertEquals(2, members.size)
    }

    @Test
    fun addMember_duplicateNameInSameGroup_isRejected() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann")).getOrThrow()

        val result = repository.addMember(groupId, "ann")

        assertTrue(result.isFailure)
    }

    @Test
    fun addMember_sameNameInDifferentGroup_isAllowed() = runBlocking {
        val groupAId = repository.createGroup("Trip", listOf("Ann")).getOrThrow()
        val groupBId = repository.createGroup("Roommates", emptyList()).getOrThrow()

        val result = repository.addMember(groupBId, "Ann")

        assertTrue(result.isSuccess)
        assertEquals(1, repository.observeMembers(groupAId).first().size)
        assertEquals(1, repository.observeMembers(groupBId).first().size)
    }

    @Test
    fun createExpense_payerFromAnotherGroup_isRejected() = runBlocking {
        val groupAId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val groupBId = repository.createGroup("Roommates", listOf("Cal", "Dee")).getOrThrow()
        val calId = repository.observeMembers(groupBId).first().first { it.name == "Cal" }.id

        val result = repository.createExpense(
            groupId = groupAId, title = "Dinner", amountCents = 1_000L, dateEpochDay = 0L,
            paidByMemberId = calId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(calId to 1_000L)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun createExpense_shareForMemberFromAnotherGroup_isRejected() = runBlocking {
        val groupAId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val groupBId = repository.createGroup("Roommates", listOf("Cal")).getOrThrow()
        val annId = repository.observeMembers(groupAId).first().first { it.name == "Ann" }.id
        val calId = repository.observeMembers(groupBId).first().first { it.name == "Cal" }.id

        val result = repository.createExpense(
            groupId = groupAId, title = "Dinner", amountCents = 1_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 500L, calId to 500L)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun createExpense_sharesNotEqualToTotal_isRejected() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val annId = repository.observeMembers(groupId).first().first { it.name == "Ann" }.id
        val benId = repository.observeMembers(groupId).first().first { it.name == "Ben" }.id

        val result = repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 1_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.CUSTOM, note = "",
            shares = mapOf(annId to 400L, benId to 400L)
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun deleteMember_referencedByExpense_isBlocked() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 1_000L, benId to 1_000L)
        ).getOrThrow()

        val result = repository.deleteMember(members.first { it.name == "Ben" })

        assertTrue(result.isFailure)
        assertEquals(2, repository.observeMembers(groupId).first().size)
    }

    @Test
    fun deleteMember_neverReferenced_succeeds() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val ben = repository.observeMembers(groupId).first().first { it.name == "Ben" }

        val result = repository.deleteMember(ben)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.observeMembers(groupId).first().size)
    }

    @Test
    fun deletingGroup_removesAllItsExpensesAndMembers() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 1_000L, benId to 1_000L)
        ).getOrThrow()
        val group = repository.getGroup(groupId)!!

        repository.deleteGroup(group)

        assertTrue(repository.observeMembers(groupId).first().isEmpty())
        assertTrue(repository.observeExpenses(groupId).first().isEmpty())
    }

    @Test
    fun updateExpense_replacesSharesAndBalancesReflectChange() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben", "Cal")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        val calId = members.first { it.name == "Cal" }.id
        val expenseId = repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 1_000L, benId to 1_000L)
        ).getOrThrow()

        repository.updateExpense(
            id = expenseId, groupId = groupId, title = "Dinner", amountCents = 3_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 1_000L, benId to 1_000L, calId to 1_000L)
        ).getOrThrow()

        val shares = repository.getShares(expenseId)
        assertEquals(3, shares.size)
        assertEquals(3_000L, shares.sumOf { it.shareAmountCents })
    }

    @Test
    fun customExpense_savedSharesRoundTripExactly() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben", "Cal")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        val calId = members.first { it.name == "Cal" }.id

        val expenseId = repository.createExpense(
            groupId = groupId, title = "Custom split", amountCents = 50_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.CUSTOM, note = "",
            shares = mapOf(annId to 15_000L, benId to 25_000L, calId to 10_000L)
        ).getOrThrow()

        val shares = repository.getShares(expenseId).associate { it.memberId to it.shareAmountCents }
        assertEquals(15_000L, shares[annId])
        assertEquals(25_000L, shares[benId])
        assertEquals(10_000L, shares[calId])
    }

    @Test
    fun updatingExpense_customToEqual_replacesSharesWithEqualSplit() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        val expenseId = repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 10_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.CUSTOM, note = "",
            shares = mapOf(annId to 3_000L, benId to 7_000L)
        ).getOrThrow()

        repository.updateExpense(
            id = expenseId, groupId = groupId, title = "Dinner", amountCents = 10_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 5_000L, benId to 5_000L)
        ).getOrThrow()

        val shares = repository.getShares(expenseId)
        assertEquals(2, shares.size)
        assertTrue(shares.all { it.shareAmountCents == 5_000L })
        assertEquals(GroupSplitMethod.EQUAL, repository.getExpense(expenseId)?.splitMethod)
    }

    @Test
    fun updatingExpense_equalToCustom_replacesSharesWithCustomAmounts() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        val expenseId = repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 10_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL, note = "",
            shares = mapOf(annId to 5_000L, benId to 5_000L)
        ).getOrThrow()

        repository.updateExpense(
            id = expenseId, groupId = groupId, title = "Dinner", amountCents = 10_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.CUSTOM, note = "",
            shares = mapOf(annId to 2_000L, benId to 8_000L)
        ).getOrThrow()

        val shares = repository.getShares(expenseId).associate { it.memberId to it.shareAmountCents }
        assertEquals(2_000L, shares[annId])
        assertEquals(8_000L, shares[benId])
        assertEquals(GroupSplitMethod.CUSTOM, repository.getExpense(expenseId)?.splitMethod)
    }

    @Test
    fun updatingExpense_changingPayerAndParticipants_replacesSharesFully() = runBlocking {
        val groupId = repository.createGroup("Trip", listOf("Ann", "Ben", "Cal")).getOrThrow()
        val members = repository.observeMembers(groupId).first()
        val annId = members.first { it.name == "Ann" }.id
        val benId = members.first { it.name == "Ben" }.id
        val calId = members.first { it.name == "Cal" }.id
        val expenseId = repository.createExpense(
            groupId = groupId, title = "Dinner", amountCents = 10_000L, dateEpochDay = 0L,
            paidByMemberId = annId, splitMethod = GroupSplitMethod.CUSTOM, note = "",
            shares = mapOf(annId to 5_000L, benId to 5_000L)
        ).getOrThrow()

        // New payer (Ben), Ann dropped, Cal added.
        repository.updateExpense(
            id = expenseId, groupId = groupId, title = "Dinner", amountCents = 10_000L, dateEpochDay = 0L,
            paidByMemberId = benId, splitMethod = GroupSplitMethod.CUSTOM, note = "",
            shares = mapOf(benId to 6_000L, calId to 4_000L)
        ).getOrThrow()

        val updated = repository.getExpense(expenseId)!!
        assertEquals(benId, updated.paidByMemberId)
        val shareMemberIds = repository.getShares(expenseId).map { it.memberId }.toSet()
        assertEquals(setOf(benId, calId), shareMemberIds)
    }
}
