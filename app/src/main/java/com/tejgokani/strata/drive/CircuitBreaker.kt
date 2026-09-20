package com.tejgokani.strata.drive

import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.engine.CircuitState

/**
 * Per-account circuit breaker. A tripped circuit simply stops being chosen by the Allocator
 * (plan §6.3/§6.8) — it is not treated as evidence the account or its chunks are gone.
 */
class CircuitBreaker(
    private val clock: StrataClock,
    private val openDurationMs: Long = 30_000,
    private val failureThreshold: Int = 5,
) {
    private var state: CircuitState = CircuitState.CLOSED
    private var openUntilMs: Long = 0
    private var consecutiveFailures: Int = 0

    @Synchronized
    fun currentState(): CircuitState {
        if (state == CircuitState.OPEN && clock.monotonicMs() >= openUntilMs) {
            state = CircuitState.HALF_OPEN
        }
        return state
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
        state = CircuitState.CLOSED
    }

    @Synchronized
    fun recordFailure() {
        consecutiveFailures += 1
        if (state == CircuitState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = CircuitState.OPEN
            openUntilMs = clock.monotonicMs() + openDurationMs
            consecutiveFailures = 0
        }
    }
}

/** Which HTTP outcomes are worth retrying (plan §6.8) — never 404, never storageQuotaExceeded. */
object DriveErrors {
    val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)

    fun isRetryable(httpCode: Int): Boolean = httpCode in RETRYABLE_HTTP_CODES

    fun isQuotaExceeded(httpCode: Int, bodySnippet: String?): Boolean =
        httpCode == 403 && bodySnippet?.contains("storageQuotaExceeded", ignoreCase = true) == true
}
