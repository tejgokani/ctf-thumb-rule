package com.tejgokani.strata.auth

import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class CachedToken(val token: String, val expiresAtMs: Long)

/**
 * Access tokens are NEVER persisted (plan §5) — only kept in memory for the process lifetime,
 * refreshed with a 5-minute safety margin ahead of Play Services' ~1h expiry.
 *
 * Implements the R8 guard: AuthorizationClient has no reliable `clearToken()`-driven
 * invalidation path here, so a 401 could in principle hand back the very token that just failed.
 * If a forced refresh returns a byte-identical token to the one that just 401'd, that is treated
 * as a hard failure — never retried — to avoid spinning forever on a stale cached token.
 */
class TokenProvider(
    private val authorizer: AccountAuthorizer,
    private val clock: StrataClock,
) {
    private val cache = mutableMapOf<String, CachedToken>()
    private val mutex = Mutex()
    private val safetyMarginMs = 5 * 60_000L

    suspend fun getToken(email: String, forceRefresh: Boolean = false): StrataResult<String> = mutex.withLock {
        val cached = cache[email]
        if (!forceRefresh && cached != null && cached.expiresAtMs - clock.wallClockMs() > safetyMarginMs) {
            return@withLock StrataResult.Ok(cached.token)
        }
        val previousToken = cached?.token

        when (val outcome = authorizer.authorize(email)) {
            is AuthOutcome.Authorized -> {
                if (forceRefresh && previousToken != null && previousToken == outcome.accessToken) {
                    StrataResult.Err(StrataError.Fatal("token refresh returned the identical stale token — hard-failing to avoid a retry loop"))
                } else {
                    cache[email] = CachedToken(outcome.accessToken, clock.wallClockMs() + 55 * 60_000L)
                    StrataResult.Ok(outcome.accessToken)
                }
            }
            is AuthOutcome.NeedsConsentPendingIntent -> StrataResult.Err(StrataError.NeedsConsent(email))
            AuthOutcome.AccountMissing -> StrataResult.Err(StrataError.AccountMissing(email))
            AuthOutcome.AccessRevoked -> StrataResult.Err(StrataError.AccessRevoked(email))
            is AuthOutcome.Failed -> StrataResult.Err(outcome.error)
        }
    }

    fun invalidate(email: String) {
        cache.remove(email)
    }
}
