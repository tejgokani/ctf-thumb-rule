package com.tejgokani.strata.data.prefs

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tejgokani.strata.core.Ids
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "strata_settings")

/**
 * Small persisted values that must survive process death: device/pool identity, the manifest
 * WAL's per-device sequence counter (must never reset or go backwards — plan §6.6), and the
 * reboot-detection state the token bucket relies on (plan §6.8/R: a reboot must reset rate-limit
 * bookkeeping, never grant a free burst of "elapsed" time that didn't really elapse for the app).
 *
 * Deliberately NOT DataStore-backed for anything security sensitive — no key material, no
 * passphrase, no access token ever touches this file (plan §5/§6.2).
 */
class SettingsStore(private val context: Context) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val POOL_ID = stringPreferencesKey("pool_id")
        val KDF_SALT_B64 = stringPreferencesKey("kdf_salt_b64")
        val KDF_ITERATIONS = longPreferencesKey("kdf_iterations")
        val MANIFEST_SEQ = longPreferencesKey("manifest_seq")
        val BOOT_ESTIMATE_MS = longPreferencesKey("boot_estimate_ms")
        val BOOT_ID = stringPreferencesKey("boot_id")
        val VAULT_CANARY_B64 = stringPreferencesKey("vault_canary_b64")
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.dataStore.data.first()
        prefs[Keys.DEVICE_ID]?.let { return it }
        val id = "device-${UUID.randomUUID()}"
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
        return id
    }

    suspend fun getPoolId(): String? = context.dataStore.data.first()[Keys.POOL_ID]

    suspend fun getOrCreatePoolId(): String {
        getPoolId()?.let { return it }
        val id = "pool-${UUID.randomUUID()}"
        context.dataStore.edit { it[Keys.POOL_ID] = id }
        return id
    }

    suspend fun getKdfParams(): Pair<String, Long>? {
        val prefs = context.dataStore.data.first()
        val salt = prefs[Keys.KDF_SALT_B64] ?: return null
        val iterations = prefs[Keys.KDF_ITERATIONS] ?: return null
        return salt to iterations
    }

    suspend fun setKdfParams(saltB64: String, iterations: Long) {
        context.dataStore.edit {
            it[Keys.KDF_SALT_B64] = saltB64
            it[Keys.KDF_ITERATIONS] = iterations
        }
    }

    /**
     * A value encrypted with the newly-derived vault at creation time, stored so a later unlock
     * attempt can be verified IMMEDIATELY (wrong passphrase -> decryption fails right away)
     * rather than silently deriving a wrong key and only discovering it much later when the
     * first real chunk fails to decrypt.
     */
    suspend fun setVaultCanary(canaryB64: String) {
        context.dataStore.edit { it[Keys.VAULT_CANARY_B64] = canaryB64 }
    }

    suspend fun getVaultCanary(): String? = context.dataStore.data.first()[Keys.VAULT_CANARY_B64]

    /** Atomically returns the next per-device manifest WAL sequence number. Never resets, never decreases. */
    suspend fun nextManifestSeq(): Long {
        var result = 0L
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MANIFEST_SEQ] ?: 0L
            result = current
            prefs[Keys.MANIFEST_SEQ] = current + 1
        }
        return result
    }

    /**
     * Returns a boot id that changes if and only if the device has actually rebooted since the
     * last call — detected by comparing `wallClock - elapsedRealtime` (a value that stays
     * constant across app restarts but jumps when the device reboots) against the last persisted
     * estimate, with a small tolerance for clock drift.
     */
    suspend fun getOrCreateBootId(): String {
        val nowEstimate = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val prefs = context.dataStore.data.first()
        val lastEstimate = prefs[Keys.BOOT_ESTIMATE_MS]
        val lastBootId = prefs[Keys.BOOT_ID]
        val toleranceMs = 2_000L

        return if (lastEstimate != null && lastBootId != null && kotlin.math.abs(nowEstimate - lastEstimate) < toleranceMs) {
            lastBootId
        } else {
            val newBootId = Ids.newId()
            context.dataStore.edit {
                it[Keys.BOOT_ESTIMATE_MS] = nowEstimate
                it[Keys.BOOT_ID] = newBootId
            }
            newBootId
        }
    }
}
