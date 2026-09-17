package com.tejgokani.strata.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.tejgokani.strata.core.StrataError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** drive.file + drive.appdata are both non-sensitive scopes (R1) — no restricted-scope review needed. */
object DriveScopes {
    val FILE: Scope = Scope("https://www.googleapis.com/auth/drive.file")
    val APPDATA: Scope = Scope("https://www.googleapis.com/auth/drive.appdata")
    val ALL: List<Scope> = listOf(FILE, APPDATA)
}

/**
 * Distinguishes the four node states from plan §5. Critically, [NeedsConsentPendingIntent]
 * returned from a BACKGROUND call is NOT a failure and must never be treated as one — no retry
 * budget, no circuit trip, no "chunk missing" classification (ScrubEngine's admissibility rule
 * depends on this distinction never being blurred).
 */
sealed class AuthOutcome {
    data class Authorized(val accessToken: String) : AuthOutcome()
    data class NeedsConsentPendingIntent(val pendingIntent: android.app.PendingIntent) : AuthOutcome()
    object AccountMissing : AuthOutcome()
    object AccessRevoked : AuthOutcome()
    data class Failed(val error: StrataError) : AuthOutcome()
}

/**
 * Wraps Play Services' `Identity.getAuthorizationClient()` — the only supported multi-account
 * auth path on Android (R4: custom URI scheme redirects are dead; R5: `setAccount()` authorizes
 * one specific account and, after first consent, returns tokens silently even from background).
 */
class AccountAuthorizer(context: Context) {
    private val client: AuthorizationClient = Identity.getAuthorizationClient(context)

    suspend fun authorize(email: String): AuthOutcome {
        val account = Account(email, "com.google")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(DriveScopes.ALL)
            .setAccount(account)
            .build()

        return try {
            val result = awaitAuthorize(request)
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) AuthOutcome.NeedsConsentPendingIntent(pendingIntent)
                else AuthOutcome.Failed(StrataError.Fatal("hasResolution()==true but no PendingIntent was supplied"))
            } else {
                val token = result.accessToken
                if (token != null) AuthOutcome.Authorized(token)
                else AuthOutcome.Failed(StrataError.Fatal("authorize() succeeded with neither a token nor a resolution"))
            }
        } catch (e: ApiException) {
            classifyApiException(e)
        } catch (e: Exception) {
            AuthOutcome.Failed(StrataError.NetworkError(e))
        }
    }

    /** Feed the Activity result here after launching [AuthOutcome.NeedsConsentPendingIntent]'s intent sender. */
    fun resultFromActivityIntent(data: Intent?): AuthOutcome {
        if (data == null) {
            // A null result means the consent Activity never returned a payload at all — either
            // the user backed out, or (more diagnostically interesting) the IntentSender never
            // actually resolved to a visible screen in the first place. Surfaced distinctly
            // rather than silently handed to Play Services' parser, which may or may not cope
            // with a null Intent the same way across versions.
            return AuthOutcome.Failed(StrataError.Fatal(
                "Consent activity returned no result (data was null) — the account chooser/" +
                    "consent screen likely never displayed, or was dismissed before completing."
            ))
        }
        return try {
            val result = client.getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token != null) AuthOutcome.Authorized(token)
            else AuthOutcome.Failed(StrataError.Fatal("consent flow completed but no access token was returned"))
        } catch (e: ApiException) {
            classifyApiException(e)
        } catch (e: Exception) {
            AuthOutcome.Failed(StrataError.Fatal("getAuthorizationResultFromIntent threw ${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    private fun classifyApiException(e: ApiException): AuthOutcome {
        // Play Services does not expose one canonical status code that reliably distinguishes
        // "this Android account no longer exists on the device" from a generic auth failure.
        // We deliberately do NOT guess a specific STATE mapping here: ScrubEngine's admissibility
        // rule means any such misclassification can only ever cause "skip this account for now",
        // never a wrongful deletion — so erring toward the generic retryable Failed is safe.
        //
        // We DO surface the raw status code + its human-readable name, though — e.g.
        // DEVELOPER_ERROR (10) almost always means the OAuth client's package+SHA-1 registered
        // in Google Cloud Console doesn't match this exact signed APK; SIGN_IN_REQUIRED or
        // CANCELED usually mean the user or the system dismissed the flow. Without this, every
        // failure looks identical from the UI and is nearly impossible to diagnose remotely.
        val statusName = com.google.android.gms.common.api.CommonStatusCodes.getStatusCodeString(e.statusCode)
        val detail = "status=${e.statusCode} ($statusName)${e.message?.let { " — $it" } ?: ""}"
        return AuthOutcome.Failed(StrataError.Fatal("Google API error: $detail"))
    }

    private suspend fun awaitAuthorize(request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            client.authorize(request)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
