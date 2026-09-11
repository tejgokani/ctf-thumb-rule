package com.tejgokani.strata.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tejgokani.strata.crypto.Kdf
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.crypto.RecoveryKey
import com.tejgokani.strata.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Base64

enum class AppPhase { LOADING, NEEDS_SETUP, LOCKED, READY }

data class BootUiState(
    val phase: AppPhase = AppPhase.LOADING,
    val passphrase: String = "",
    val passphraseConfirm: String = "",
    val recoveryKeyToShow: String? = null,
    val recoveryKeyAcknowledged: Boolean = false,
    val error: String? = null,
    val unlocking: Boolean = false,
)

/** Passphrase strength — a simple, honest heuristic (length + character class variety), not a claim of cryptographic entropy measurement. */
fun passphraseStrength(passphrase: String): Float {
    if (passphrase.isEmpty()) return 0f
    var score = 0
    if (passphrase.length >= 8) score++
    if (passphrase.length >= 14) score++
    if (passphrase.any { it.isDigit() }) score++
    if (passphrase.any { it.isUpperCase() } && passphrase.any { it.isLowerCase() }) score++
    if (passphrase.any { !it.isLetterOrDigit() }) score++
    return (score / 5f).coerceIn(0f, 1f)
}

class BootViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BootUiState())
    val state: StateFlow<BootUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.initialize()
            val hasVault = container.settings.getKdfParams() != null
            _state.value = _state.value.copy(phase = if (hasVault) AppPhase.LOCKED else AppPhase.NEEDS_SETUP)
        }
    }

    fun setPassphrase(v: String) { _state.value = _state.value.copy(passphrase = v, error = null) }
    fun setPassphraseConfirm(v: String) { _state.value = _state.value.copy(passphraseConfirm = v, error = null) }

    fun createVault() {
        val s = _state.value
        if (s.passphrase.length < 8) {
            _state.value = s.copy(error = "Passphrase must be at least 8 characters")
            return
        }
        if (s.passphrase != s.passphraseConfirm) {
            _state.value = s.copy(error = "Passphrases do not match")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(unlocking = true)
            val salt = Kdf.randomSalt()
            val vault = KeyVault.fromPassphrase(s.passphrase.toCharArray(), salt, Kdf.DEFAULT_ITERATIONS)
            container.settings.setKdfParams(Base64.getEncoder().encodeToString(salt), Kdf.DEFAULT_ITERATIONS.toLong())
            container.settings.setVaultCanary(
                Base64.getEncoder().encodeToString(vault.sealAppProperties(VAULT_CANARY_PLAINTEXT.toByteArray()))
            )
            container.unlock(vault)
            _state.value = _state.value.copy(
                unlocking = false,
                recoveryKeyToShow = vault.exportRecoveryKey(),
            )
        }
    }

    fun acknowledgeRecoveryKey() {
        _state.value = _state.value.copy(recoveryKeyAcknowledged = true, phase = AppPhase.READY)
    }

    fun unlockWithPassphrase() {
        viewModelScope.launch {
            _state.value = _state.value.copy(unlocking = true, error = null)
            val params = container.settings.getKdfParams()
            if (params == null) {
                _state.value = _state.value.copy(unlocking = false, error = "No vault found on this device")
                return@launch
            }
            val (saltB64, iterations) = params
            val salt = Base64.getDecoder().decode(saltB64)
            val vault = KeyVault.fromPassphrase(_state.value.passphrase.toCharArray(), salt, iterations.toInt())
            if (!verifyCanary(vault)) {
                _state.value = _state.value.copy(unlocking = false, error = "Incorrect passphrase")
                return@launch
            }
            container.unlock(vault)
            _state.value = _state.value.copy(unlocking = false, phase = AppPhase.READY)
        }
    }

    fun unlockWithRecoveryKey(code: String) {
        viewModelScope.launch {
            val vault = KeyVault.fromRecoveryKey(code)
            if (vault == null) {
                _state.value = _state.value.copy(error = "Invalid recovery key")
                return@launch
            }
            if (!verifyCanary(vault)) {
                _state.value = _state.value.copy(error = "This recovery key does not match this device's vault")
                return@launch
            }
            container.unlock(vault)
            _state.value = _state.value.copy(phase = AppPhase.READY, error = null)
        }
    }

    /** Returns true iff [vault] can decrypt the canary written at vault-creation time — i.e. the correct key. */
    private suspend fun verifyCanary(vault: KeyVault): Boolean {
        val canaryB64 = container.settings.getVaultCanary() ?: return true // no canary yet (shouldn't happen post-setup)
        return try {
            val plaintext = vault.openAppProperties(Base64.getDecoder().decode(canaryB64))
            String(plaintext) == VAULT_CANARY_PLAINTEXT
        } catch (e: Exception) {
            false
        }
    }

    /** Returns to the lock screen without forgetting the on-device vault — used by Settings' "LOCK VAULT". */
    fun relock() {
        container.lock()
        _state.value = _state.value.copy(phase = AppPhase.LOCKED, passphrase = "", passphraseConfirm = "", error = null)
    }

    companion object {
        private const val VAULT_CANARY_PLAINTEXT = "strata-vault-canary-v1"
    }
}
