package com.tejgokani.strata.engine

import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllocatorTest {
    private val chunkSize = 1_000_000L // 1 MB for readable test numbers

    private fun account(id: String, freeBytes: Long, usageRatio: Double = 0.1, eligible: Boolean = true) =
        Allocator.AccountView(id, freeBytes, usageRatio, eligible)

    @Test fun `apportionment is exactly proportional to free space`() {
        // A has 4x B's free space -> A should get ~4x the chunks.
        val accounts = listOf(account("A", freeBytes = chunkSize * 80), account("B", freeBytes = chunkSize * 20))
        val plan = (Allocator.planStripe(100, chunkSize, accounts) as StrataResult.Ok).value
        val countA = plan.chunkIndexToAccountId.values.count { it == "A" }
        val countB = plan.chunkIndexToAccountId.values.count { it == "B" }
        assertEquals(100, countA + countB)
        assertEquals(80, countA)
        assertEquals(20, countB)
    }

    @Test fun `apportionment stays low variance across many equal accounts, unlike naive random choice`() {
        val accounts = (1..10).map { account("acct-$it", freeBytes = chunkSize * 100) }
        val plan = (Allocator.planStripe(16, chunkSize, accounts) as StrataResult.Ok).value
        val counts = plan.chunkIndexToAccountId.values.groupingBy { it }.eachCount()
        // 16 chunks over 10 equal accounts: the largest-remainder method must never let any one
        // account take more than a couple of chunks above the even share (this is precisely the
        // failure mode plan §6.3 rejects power-of-two-choices for at this scale).
        assertTrue("max share was ${counts.values.max()}, expected <= 3", counts.values.max()!! <= 3)
        assertEquals(16, counts.values.sum())
    }

    @Test fun `never assigns a chunk to an ineligible account`() {
        val accounts = listOf(account("healthy", freeBytes = chunkSize * 50), account("broken", freeBytes = chunkSize * 50, eligible = false))
        val plan = (Allocator.planStripe(10, chunkSize, accounts) as StrataResult.Ok).value
        assertTrue(plan.chunkIndexToAccountId.values.all { it == "healthy" })
    }

    @Test fun `never assigns a chunk to an account above 90 percent usage`() {
        val accounts = listOf(account("nearlyFull", freeBytes = chunkSize * 50, usageRatio = 0.95), account("ok", freeBytes = chunkSize * 50, usageRatio = 0.5))
        val plan = (Allocator.planStripe(10, chunkSize, accounts) as StrataResult.Ok).value
        assertTrue(plan.chunkIndexToAccountId.values.all { it == "ok" })
    }

    @Test fun `refuses up front when the pool cannot fit the file, never partially plans it`() {
        val accounts = listOf(account("small", freeBytes = chunkSize * 5))
        val result = Allocator.planStripe(10, chunkSize, accounts)
        assertTrue(result is StrataResult.Err)
        assertTrue((result as StrataResult.Err).error is StrataError.PoolFull)
    }

    @Test fun `mirror is chosen on a distinct account from the primary`() {
        val accounts = listOf(account("primary", freeBytes = chunkSize * 10), account("secondary", freeBytes = chunkSize * 10))
        val mirror = Allocator.chooseMirror(accounts, chunkSize, excludeAccountIds = setOf("primary"))
        assertEquals("secondary", (mirror as StrataResult.Ok).value)
    }

    @Test fun `mirror fails closed rather than reusing the primary when no other account is eligible`() {
        val accounts = listOf(account("onlyOne", freeBytes = chunkSize * 10))
        val mirror = Allocator.chooseMirror(accounts, chunkSize, excludeAccountIds = setOf("onlyOne"))
        assertTrue(mirror is StrataResult.Err)
    }

    @Test fun `repair placement excludes the failed account`() {
        val accounts = listOf(account("failed", freeBytes = chunkSize * 10), account("survivor", freeBytes = chunkSize * 10))
        repeat(20) {
            val choice = Allocator.chooseForRepair(accounts, chunkSize, excludeAccountIds = setOf("failed"))
            assertEquals("survivor", (choice as StrataResult.Ok).value)
        }
    }
}
