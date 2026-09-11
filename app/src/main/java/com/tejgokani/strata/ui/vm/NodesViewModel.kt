package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.auth.AuthOutcome
import com.tejgokani.strata.core.CanonicalValue
import com.tejgokani.strata.di.AppContainer
import com.tejgokani.strata.engine.AccountRecord
import com.tejgokani.strata.engine.AuthState
import com.tejgokani.strata.engine.CircuitState
import com.tejgokani.strata.ledger.LedgerEventType
import com.tejgokani.strata.ui.ActivityResultBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NodesUiState(
    val accounts: List<AccountRecord> = emptyList(),
    val addingNode: Boolean = false,
    val error: String? = null,
)

class NodesViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(NodesUiState())
    val state: StateFlow<NodesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(accounts = container.accountRepository.all())
        }
    }

    /** Full add-node flow (plan §5): pick an account, authorize it, fetch quota, persist, log. */
    fun addNode(bridge: ActivityResultBridge) {
        viewModelScope.launch {
            _state.value = _state.value.copy(addingNode = true, error = null)
            try {
                addNodeInternal(bridge)
            } catch (e: Exception) {
                // Never let an unexpected exception here crash the app silently — a stuck
                // "adding node" spinner with no explanation is worse than an ugly but honest
                // error message naming the real exception type.
                android.util.Log.e("Strata", "addNode() failed", e)
                _state.value = _state.value.copy(addingNode = false, error = "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private suspend fun addNodeInternal(bridge: ActivityResultBridge) {
            val email = bridge.pickGoogleAccount()
            if (email == null) {
                _state.value = _state.value.copy(addingNode = false)
                return
            }

            when (val outcome = container.accountAuthorizer.authorize(email)) {
                is AuthOutcome.NeedsConsentPendingIntent -> {
                    val resultIntent = bridge.launchConsent(outcome.pendingIntent)
                    val finalOutcome = container.accountAuthorizer.resultFromActivityIntent(resultIntent)
                    if (finalOutcome is AuthOutcome.Authorized) {
                        finishAddingNode(email)
                    } else {
                        // Surface whatever detail is available rather than a generic message —
                        // this is exactly the information needed to tell "user cancelled" apart
                        // from "the OAuth client isn't registered for this package/SHA-1" apart
                        // from "the consent screen never displayed at all".
                        val detail = (finalOutcome as? AuthOutcome.Failed)?.error?.message
                            ?: "no consent for $email (${finalOutcome::class.simpleName})"
                        _state.value = _state.value.copy(addingNode = false, error = detail)
                    }
                }
                is AuthOutcome.Authorized -> finishAddingNode(email)
                is AuthOutcome.Failed -> _state.value = _state.value.copy(addingNode = false, error = outcome.error.message)
                AuthOutcome.AccountMissing -> _state.value = _state.value.copy(addingNode = false, error = "Account not found on this device")
                AuthOutcome.AccessRevoked -> _state.value = _state.value.copy(addingNode = false, error = "Access was revoked for $email")
            }
    }

    private suspend fun finishAddingNode(email: String) {
        val accountId = email // stable, human-legible id; Drive scopes are per-account anyway
        val quotaResult = container.driveBackend.getQuota(accountId)
        val quota = quotaResult.getOrNull()
        val record = AccountRecord(
            id = accountId, email = email,
            quotaLimitBytes = quota?.limitBytes ?: 15_000_000_000L,
            quotaUsageBytes = quota?.usageBytes ?: 0L,
            quotaUsageInDriveTrashBytes = quota?.usageInDriveTrashBytes ?: 0L,
            reservedBytes = 0L, authState = AuthState.OK, circuitState = CircuitState.CLOSED,
            circuitOpenUntilMs = 0L, dailyUploadedBytes = 0L, dailyBudgetResetAtMs = 0L,
            lastFullEnumerationMs = 0L, lastQuotaRefreshMs = container.clock.wallClockMs(),
        )
        container.accountRepository.upsert(record)
        container.ledger.append(LedgerEventType.NODE_ADDED, CanonicalValue.map(
            "accountId" to CanonicalValue.of(accountId), "email" to CanonicalValue.of(email),
        ))
        _state.value = _state.value.copy(addingNode = false, accounts = container.accountRepository.all())
    }

    fun refreshQuota(accountId: String) {
        viewModelScope.launch {
            val record = container.accountRepository.get(accountId) ?: return@launch
            val quota = container.driveBackend.getQuota(accountId).getOrNull() ?: return@launch
            container.accountRepository.upsert(record.copy(
                quotaLimitBytes = quota.limitBytes, quotaUsageBytes = quota.usageBytes,
                quotaUsageInDriveTrashBytes = quota.usageInDriveTrashBytes,
                lastQuotaRefreshMs = container.clock.wallClockMs(),
            ))
            refresh()
        }
    }
}
