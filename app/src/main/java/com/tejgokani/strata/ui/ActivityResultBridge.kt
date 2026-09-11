package com.tejgokani.strata.ui

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf

/**
 * Bridges Compose to the two Activity Result flows this app needs (plan §5):
 *  1. the system account picker, to choose WHICH Google account to bind as a node, and
 *  2. the Play Services consent PendingIntent, launched only when `hasResolution()==true`.
 *
 * Hoisted at MainActivity (Activity Result launchers must be registered before STARTED) and
 * threaded down through composition rather than through every screen's parameter list.
 */
interface ActivityResultBridge {
    /** Launches the system "choose a Google account" picker; result is the chosen email, or null if cancelled. */
    suspend fun pickGoogleAccount(): String?

    /** Launches a consent PendingIntent obtained from AuthorizationClient; result is the resulting Intent, or null if cancelled. */
    suspend fun launchConsent(pendingIntent: PendingIntent): Intent?

    /** SAF "open a document to upload" picker; result is the chosen file's content Uri, or null if cancelled. */
    suspend fun pickDocumentToUpload(): Uri?

    /** SAF "save a document" picker for downloads; result is the destination content Uri, or null if cancelled. */
    suspend fun pickDestinationForDownload(suggestedName: String, mimeType: String): Uri?
}

val LocalActivityResultBridge = compositionLocalOf<ActivityResultBridge> {
    error("No ActivityResultBridge provided — must be set from MainActivity")
}
