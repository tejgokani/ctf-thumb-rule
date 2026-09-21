package com.tejgokani.strata.drive

import com.tejgokani.strata.core.StrataClock
import kotlin.math.min

/**
 * Per-account token bucket (plan §6.8). Backoff/rate limiting is scoped per account so one
 * throttled node can never stall transfers to the rest of the pool.
 *
 * Uses [StrataClock.monotonicMs] (elapsedRealtime on device), not wall clock, and tracks
 * [StrataClock.bootId] so a reboot resets the bucket instead of silently granting a burst of
 * "free" tokens accumulated while the illusion of time passed during a power-off.
 */
class TokenBucket(
    private val capacity: Double,
    private val refillPerSecond: Double,
    private val clock: StrataClock,
) {
    private var tokens: Double = capacity
    private var lastRefillMs: Long = clock.monotonicMs()
    private var lastBootId: String = clock.bootId()

    @Synchronized
    fun tryAcquire(cost: Double = 1.0): Boolean {
        refill()
        return if (tokens >= cost) {
            tokens -= cost
            true
        } else {
            false
        }
    }

    @Synchronized
    fun msUntilAvailable(cost: Double = 1.0): Long {
        refill()
        if (tokens >= cost) return 0
        val deficit = cost - tokens
        return ((deficit / refillPerSecond) * 1000).toLong().coerceAtLeast(1)
    }

    private fun refill() {
        if (clock.bootId() != lastBootId) {
            // Device rebooted — elapsedRealtime reset to 0, do not grant a false burst.
            lastBootId = clock.bootId()
            lastRefillMs = clock.monotonicMs()
            tokens = capacity
            return
        }
        val now = clock.monotonicMs()
        val elapsedSeconds = (now - lastRefillMs).coerceAtLeast(0) / 1000.0
        tokens = min(capacity, tokens + elapsedSeconds * refillPerSecond)
        lastRefillMs = now
    }
}

/** Truncated exponential backoff with full jitter (plan §6.8): min(2^n + jitter, cap). */
object Backoff {
    fun delayMs(attempt: Int, baseMs: Long = 1000, capMs: Long = 64_000, random: java.util.Random = java.util.Random()): Long {
        val exp = (baseMs * (1L shl attempt.coerceIn(0, 20))).coerceAtMost(capMs)
        return (random.nextDouble() * exp).toLong().coerceAtLeast(1)
    }
}
