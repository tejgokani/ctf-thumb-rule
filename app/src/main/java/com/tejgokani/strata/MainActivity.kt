package com.tejgokani.strata

import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tejgokani.strata.ui.ActivityResultBridge
import com.tejgokani.strata.ui.LocalActivityResultBridge
import com.tejgokani.strata.ui.nav.StrataNavHost
import com.tejgokani.strata.ui.screens.boot.BootScreen
import com.tejgokani.strata.ui.theme.StrataTheme
import com.tejgokani.strata.ui.vm.AppPhase
import com.tejgokani.strata.ui.vm.BootViewModel
import com.tejgokani.strata.ui.vm.StrataViewModelFactory
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Hosts the two Activity Result flows that must live at the Activity level (plan §5): the system
 * account picker (choosing WHICH Google account to bind) and the Play Services consent
 * PendingIntent (shown only when `hasResolution()==true`). Everything else is Compose.
 */
class MainActivity : ComponentActivity() {

    private var pendingAccountPick: CancellableContinuation<String?>? = null
    private var pendingConsent: CancellableContinuation<Intent?>? = null
    private var pendingOpenDocument: CancellableContinuation<Uri?>? = null
    private var pendingCreateDocument: CancellableContinuation<Uri?>? = null

    private lateinit var accountPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var consentLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var createDocumentLauncher: ActivityResultLauncher<String>

    private val bridge = object : ActivityResultBridge {
        override suspend fun pickGoogleAccount(): String? = suspendCancellableCoroutine { cont ->
            pendingAccountPick = cont
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null,
            )
            accountPickerLauncher.launch(intent)
        }

        override suspend fun launchConsent(pendingIntent: PendingIntent): Intent? = suspendCancellableCoroutine { cont ->
            pendingConsent = cont
            try {
                consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                // If the IntentSender itself is invalid/cancelled, launch() can throw
                // synchronously — before the callback machinery ever fires. Without this catch
                // that exception would crash the coroutine instead of surfacing a diagnosable
                // error through the normal result path.
                android.util.Log.e("Strata", "consentLauncher.launch() threw synchronously", e)
                pendingConsent = null
                cont.resumeWith(Result.failure(e))
            }
        }

        override suspend fun pickDocumentToUpload(): Uri? = suspendCancellableCoroutine { cont ->
            pendingOpenDocument = cont
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        override suspend fun pickDestinationForDownload(suggestedName: String, mimeType: String): Uri? =
            suspendCancellableCoroutine { cont ->
                pendingCreateDocument = cont
                createDocumentLauncher.launch(suggestedName)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            pendingAccountPick?.resume(email)
            pendingAccountPick = null
        }
        consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            pendingConsent?.resume(result.data)
            pendingConsent = null
        }
        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingOpenDocument?.resume(uri)
            pendingOpenDocument = null
        }
        createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            pendingCreateDocument?.resume(uri)
            pendingCreateDocument = null
        }

        val container = (application as StrataApp).container

        setContent {
            StrataTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalActivityResultBridge provides bridge) {
                        val factory = remember { StrataViewModelFactory(container) }
                        val bootViewModel: BootViewModel = viewModel(factory = factory)
                        val bootState by bootViewModel.state.collectAsState()

                        if (bootState.phase == AppPhase.READY) {
                            StrataNavHost(container, onLocked = { bootViewModel.relock() })
                        } else {
                            BootScreen(bootViewModel, onReady = {})
                        }
                    }
                }
            }
        }
    }
}
