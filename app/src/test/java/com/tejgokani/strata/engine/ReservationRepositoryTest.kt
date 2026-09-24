package com.tejgokani.strata.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationRepositoryTest {
    @Test fun `active reservations sum correctly for the account they belong to`() = runTest {
        val repo = InMemoryReservationRepository()
        repo.reserve("acct-A", 1000L, expiresAtMs = 10_000L)
        repo.reserve("acct-A", 2000L, expiresAtMs = 10_000L)
        repo.reserve("acct-B", 5000L, expiresAtMs = 10_000L)
        assertEquals(3000L, repo.activeReservedBytes("acct-A", nowMs = 0L))
        assertEquals(5000L, repo.activeReservedBytes("acct-B", nowMs = 0L))
    }

    @Test fun `expired reservations are pruned and no longer counted`() = runTest {
        val repo = InMemoryReservationRepository()
        repo.reserve("acct-A", 1000L, expiresAtMs = 5_000L)
        assertEquals(1000L, repo.activeReservedBytes("acct-A", nowMs = 4_000L))
        assertEquals(0L, repo.activeReservedBytes("acct-A", nowMs = 6_000L))
    }

    @Test fun `releasing a reservation frees its bytes immediately`() = runTest {
        val repo = InMemoryReservationRepository()
        val r = repo.reserve("acct-A", 1000L, expiresAtMs = 100_000L)
        assertEquals(1000L, repo.activeReservedBytes("acct-A", nowMs = 0L))
        repo.release(r.id)
        assertEquals(0L, repo.activeReservedBytes("acct-A", nowMs = 0L))
    }
}
