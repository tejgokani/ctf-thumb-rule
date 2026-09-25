package com.tejgokani.strata.ledger

import com.tejgokani.strata.core.CanonicalValue
import com.tejgokani.strata.core.FakeStrataClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private fun payload(n: Int) = CanonicalValue.map("n" to CanonicalValue.of(n))

    @Test fun `an empty ledger verifies trivially`() {
        val ledger = Ledger(InMemoryLedgerStore(), FakeStrataClock(), "device-1")
        assertEquals(ChainVerifyResult.Empty, ledger.verifyChain())
    }

    @Test fun `a chain of events verifies`() {
        val ledger = Ledger(InMemoryLedgerStore(), FakeStrataClock(), "device-1")
        repeat(20) { ledger.append(LedgerEventType.CHUNK_STORED, payload(it)) }
        assertEquals(ChainVerifyResult.Valid, ledger.verifyChain())
    }

    @Test fun `mutating a stored event's payload is detected`() {
        val store = InMemoryLedgerStore()
        val clock = FakeStrataClock()
        val ledger = Ledger(store, clock, "device-1")
        repeat(5) { ledger.append(LedgerEventType.FILE_ADDED, payload(it)) }
        // Directly corrupt one event as if someone edited the database out from under the ledger.
        val original = store.allInOrder()[2]
        val tampered = original.copy(payload = payload(999))
        val corruptedStore = object : LedgerStore {
            val events = store.allInOrder().toMutableList().also { it[2] = tampered }
            override fun appendRaw(event: LedgerEvent) { events.add(event) }
            override fun lastForDevice(deviceId: String) = events.lastOrNull { it.deviceId == deviceId }
            override fun allInOrder() = events.sortedBy { it.seq }
            override fun nextSeq(): Long = events.size.toLong()
        }
        val verifier = Ledger(corruptedStore, clock, "device-1")
        val result = verifier.verifyChain()
        assertTrue("expected a Broken result, got $result", result is ChainVerifyResult.Broken)
    }

    @Test fun `mutating prevHash on a later event is detected as a reordering`() {
        val store = InMemoryLedgerStore()
        val clock = FakeStrataClock()
        val ledger = Ledger(store, clock, "device-1")
        repeat(5) { ledger.append(LedgerEventType.FILE_ADDED, payload(it)) }
        val original = store.allInOrder()
        val tamperedPrevHash = original[3].copy(prevHash = Ledger.GENESIS_HASH)
        val corruptedList = original.toMutableList().also { it[3] = tamperedPrevHash }
        val corruptedStore = object : LedgerStore {
            override fun appendRaw(event: LedgerEvent) {}
            override fun lastForDevice(deviceId: String) = corruptedList.lastOrNull { it.deviceId == deviceId }
            override fun allInOrder() = corruptedList
            override fun nextSeq(): Long = corruptedList.size.toLong()
        }
        val result = Ledger(corruptedStore, clock, "device-1").verifyChain()
        assertTrue(result is ChainVerifyResult.Broken)
    }

    @Test fun `ordering is by lamport counter and survives wall clock jumping backwards`() {
        val store = InMemoryLedgerStore()
        val clock = FakeStrataClock(wall = 1_000_000L)
        val ledger = Ledger(store, clock, "device-1")
        ledger.append(LedgerEventType.NODE_ADDED, payload(1))
        clock.advanceWall(-500_000L) // simulate the user turning back their system clock
        ledger.append(LedgerEventType.NODE_ADDED, payload(2))
        clock.advanceWall(10_000L)
        ledger.append(LedgerEventType.NODE_ADDED, payload(3))

        // The chain must still verify (lamport, not wall clock, defines the order it was built in).
        assertEquals(ChainVerifyResult.Valid, ledger.verifyChain())
        val lamports = ledger.all().map { it.lamport }
        assertEquals(listOf(1L, 2L, 3L), lamports)
    }

    @Test fun `separate devices get separate chains that each verify independently`() {
        val store = InMemoryLedgerStore()
        val clock = FakeStrataClock()
        val deviceA = Ledger(store, clock, "device-A")
        val deviceB = Ledger(store, clock, "device-B")
        repeat(5) { deviceA.append(LedgerEventType.CHUNK_STORED, payload(it)) }
        repeat(5) { deviceB.append(LedgerEventType.CHUNK_STORED, payload(it)) }
        assertEquals(ChainVerifyResult.Valid, deviceA.verifyChain())
        assertEquals(10, store.allInOrder().size)
    }
}
