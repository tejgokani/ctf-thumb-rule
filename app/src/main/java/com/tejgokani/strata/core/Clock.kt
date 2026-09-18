package com.tejgokani.strata.core

/**
 * Abstraction over time so tests can control it.
 *
 * IMPORTANT (R-ledger): [wallClockMs] is advisory only — it is user-settable and must never be
 * used to order events. [monotonicMs] (backed by SystemClock.elapsedRealtime() on device) is
 * immune to wall-clock changes and NTP jumps, and is what ordering-sensitive code should prefer
 * alongside the persisted Lamport counter.
 */
interface StrataClock {
    fun wallClockMs(): Long
    fun monotonicMs(): Long
    /** Stable per-process-boot id; lets code detect "the device rebooted" for token-bucket resets. */
    fun bootId(): String
}

/** Production clock. On Android, monotonicMs should be wired to SystemClock.elapsedRealtime(). */
class SystemStrataClock(private val bootIdValue: String) : StrataClock {
    override fun wallClockMs(): Long = System.currentTimeMillis()
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
    override fun bootId(): String = bootIdValue
}

/** Deterministic clock for unit tests. */
class FakeStrataClock(
    private var wall: Long = 1_700_000_000_000L,
    private var mono: Long = 0L,
    private var boot: String = "test-boot"
) : StrataClock {
    override fun wallClockMs(): Long = wall
    override fun monotonicMs(): Long = mono
    override fun bootId(): String = boot

    fun advanceWall(ms: Long) { wall += ms }
    fun advanceMono(ms: Long) { mono += ms }
    fun reboot(newBootId: String) { boot = newBootId; mono = 0L }
}
