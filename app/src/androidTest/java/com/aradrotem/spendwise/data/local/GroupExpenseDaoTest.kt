package com.aradrotem.spendwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupExpenseDaoTest {

    private lateinit var database: SpendWiseDatabase
    private lateinit var groupDao: ExpenseGroupDao
    private lateinit var memberDao: GroupMemberDao
    private lateinit var expenseDao: GroupExpenseDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        groupDao = database.expenseGroupDao()
        memberDao = database.groupMemberDao()
        expenseDao = database.groupExpenseDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun createGroupWithMembers(vararg names: String): Pair<Long, List<Long>> {
        val groupId = groupDao.insert(ExpenseGroupEntity(name = "Trip"))
        val memberIds = names.map { name -> memberDao.insert(GroupMemberEntity(groupId = groupId, name = name)) }
        return groupId to memberIds
    }

    @Test
    fun creatingGroupWithMembers_persistsAll() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")

        val members = memberDao.getByGroup(groupId)
        assertEquals(2, members.size)
        assertEquals(memberIds.toSet(), members.map { it.id }.toSet())
    }

    @Test
    fun insertExpenseWithShares_isAtomicAndReadableTogether() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")
        val (annId, benId) = memberIds

        val expenseId = expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 4_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 2_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 2_000L)
            )
        )

        val shares = expenseDao.getSharesForExpense(expenseId)
        assertEquals(2, shares.size)
        assertEquals(4_000L, shares.sumOf { it.shareAmountCents })
        assertTrue(shares.all { it.expenseId == expenseId })
    }

    @Test
    fun updateExpenseWithShares_replacesAllPreviousShares() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben", "Cal")
        val (annId, benId, calId) = memberIds

        val expenseId = expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 4_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 2_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 2_000L)
            )
        )

        val updatedExpense = expenseDao.getById(expenseId)!!.copy(amountCents = 6_000L)
        expenseDao.updateExpenseWithShares(
            updatedExpense,
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 2_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 2_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = calId, shareAmountCents = 2_000L)
            )
        )

        val shares = expenseDao.getSharesForExpense(expenseId)
        assertEquals(3, shares.size)
        assertEquals(6_000L, shares.sumOf { it.shareAmountCents })
        assertEquals(6_000L, expenseDao.getById(expenseId)?.amountCents)
    }

    @Test
    fun deletingExpense_removesItsShares() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")
        val (annId, benId) = memberIds

        val expenseId = expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 1_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 1_000L)
            )
        )
        val expense = expenseDao.getById(expenseId)!!

        expenseDao.deleteExpense(expense)

        assertNull(expenseDao.getById(expenseId))
        assertTrue(expenseDao.getSharesForExpense(expenseId).isEmpty())
    }

    @Test
    fun deletingGroup_cascadesToMembersExpensesAndShares() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")
        val (annId, benId) = memberIds
        val expenseId = expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 1_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 1_000L)
            )
        )
        val group = groupDao.getById(groupId)!!

        groupDao.delete(group)

        assertNull(groupDao.getById(groupId))
        assertTrue(memberDao.getByGroup(groupId).isEmpty())
        assertNull(expenseDao.getById(expenseId))
        assertTrue(expenseDao.getSharesForExpense(expenseId).isEmpty())
    }

    @Test
    fun duplicateExpenseShareForSameMember_isRejectedByUniqueIndex() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")
        val (annId) = memberIds
        val expenseId = expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 2_000L))
        )

        var threw = false
        try {
            expenseDao.getSharesForExpense(expenseId) // sanity read before forcing the violation
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO group_expense_shares (expenseId, memberId, shareAmountCents) VALUES ($expenseId, $annId, 500)"
            )
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun countExpensesPaidByMemberAndCountSharesForMember_reflectUsage() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")
        val (annId, benId) = memberIds

        assertEquals(0, expenseDao.countExpensesPaidByMember(annId))
        assertEquals(0, expenseDao.countSharesForMember(benId))

        expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 1_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 1_000L)
            )
        )

        assertEquals(1, expenseDao.countExpensesPaidByMember(annId))
        assertEquals(1, expenseDao.countSharesForMember(benId))
    }

    @Test
    fun observeByGroup_reflectsInsertsReactively() = runBlocking {
        val (groupId, memberIds) = createGroupWithMembers("Ann", "Ben")
        val (annId, benId) = memberIds

        assertTrue(expenseDao.observeByGroup(groupId).first().isEmpty())

        expenseDao.insertExpenseWithShares(
            GroupExpenseEntity(
                groupId = groupId, title = "Dinner", amountCents = 2_000L, dateEpochDay = 100L,
                paidByMemberId = annId, splitMethod = GroupSplitMethod.EQUAL
            ),
            listOf(
                GroupExpenseShareEntity(expenseId = 0, memberId = annId, shareAmountCents = 1_000L),
                GroupExpenseShareEntity(expenseId = 0, memberId = benId, shareAmountCents = 1_000L)
            )
        )

        assertEquals(1, expenseDao.observeByGroup(groupId).first().size)
    }
}
