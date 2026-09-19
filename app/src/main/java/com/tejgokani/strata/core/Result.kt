package com.tejgokani.strata.core

/**
 * A small sealed result type used across the engine instead of exceptions for expected failure
 * modes (network errors, quota exhaustion, auth states). Exceptions are reserved for genuine
 * programming errors / invariant violations.
 */
sealed class StrataResult<out T> {
    data class Ok<T>(val value: T) : StrataResult<T>()
    data class Err(val error: StrataError) : StrataResult<Nothing>()

    inline fun <R> map(f: (T) -> R): StrataResult<R> = when (this) {
        is Ok -> Ok(f(value))
        is Err -> this
    }

    inline fun onOk(f: (T) -> Unit): StrataResult<T> {
        if (this is Ok) f(value)
        return this
    }

    inline fun onErr(f: (StrataError) -> Unit): StrataResult<T> {
        if (this is Err) f(error)
        return this
    }

    fun getOrNull(): T? = (this as? Ok)?.value
}

sealed class StrataError(val message: String, val retryable: Boolean) {
    data class NetworkError(val cause: Throwable?) :
        StrataError("Network error" + (cause?.message?.let { ": $it" } ?: ""), retryable = true)
    data class RateLimited(val retryAfterMs: Long) : StrataError("Rate limited", retryable = true)
    data class ServerError(val code: Int) : StrataError("Server error $code", retryable = true)
    data class NeedsConsent(val accountId: String) : StrataError("Account needs re-consent", retryable = false)
    data class AccountMissing(val accountId: String) : StrataError("Account not on device", retryable = false)
    data class AccessRevoked(val accountId: String) : StrataError("Access revoked", retryable = false)
    data class NotFound(val what: String) : StrataError("Not found: $what", retryable = false)
    data class ChecksumMismatch(val expected: String, val actual: String) :
        StrataError("Checksum mismatch", retryable = false)
    data class PoolFull(val neededBytes: Long) : StrataError("Pool full, need $neededBytes bytes", retryable = false)
    data class InvalidState(val detail: String) : StrataError("Invalid state: $detail", retryable = false)
    data class Fatal(val detail: String, val cause: Throwable? = null) : StrataError(detail, retryable = false)
}
