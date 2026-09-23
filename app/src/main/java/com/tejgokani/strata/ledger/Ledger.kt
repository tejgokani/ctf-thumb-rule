package com.tejgokani.strata.ledger

import com.tejgokani.strata.core.CanonicalCodec
import com.tejgokani.strata.core.CanonicalValue
import com.tejgokani.strata.core.StrataClock
import java.security.MessageDigest

/**
 * Every event STRATA cares about — which account a chunk went to, when, and what happened to it
 * since — is recorded here (plan §6.7). This is the mechanism behind the product requirement
 * that "every file is logged where and when and which account's storage it was sent to."
 */
enum class LedgerEventType {
    NODE_ADDED, NODE_REMOVED, NODE_REAUTH, ACCESS_REVOKED,
    FILE_ADDED, FILE_READ, FILE_DELETED,
    CHUNK_STORED, CHUNK_VERIFIED, CHUNK_MIRRORED, CHUNK_UNTRASHED, CHUNK_ADOPTED,
    CHUNK_REPAIRED, CHUNK_LOST,
    MANIFEST_REPLICATED, DRAIN_MOVED, GC_TRASHED,
    /** Embeds this device's view of other devices' chain heads — the seed of the cross-device Merkle DAG. */
    CHECKPOINT,
}

data class LedgerEvent(
    val seq: Long,
    val deviceId: String,
    val lamport: Long,
    val wallClockMs: Long,
    val monotonicMs: Long,
    val type: LedgerEventType,
    val payload: CanonicalValue,
    val prevHash: ByteArray,
    val hash: ByteArray,
)

/** Storage abstraction so Ledger logic is testable without Room/Android. */
interface LedgerStore {
    fun appendRaw(event: LedgerEvent)
    fun lastForDevice(deviceId: String): LedgerEvent?
    fun allInOrder(): List<LedgerEvent>
    fun nextSeq(): Long
}

class InMemoryLedgerStore : LedgerStore {
    private val events = mutableListOf<LedgerEvent>()
    private var seqCounter = 0L

    @Synchronized override fun appendRaw(event: LedgerEvent) { events.add(event) }

    @Synchronized override fun lastForDevice(deviceId: String): LedgerEvent? =
        events.lastOrNull { it.deviceId == deviceId }

    @Synchronized override fun allInOrder(): List<LedgerEvent> = events.sortedBy { it.seq }

    @Synchronized override fun nextSeq(): Long = seqCounter++
}

sealed class ChainVerifyResult {
    object Valid : ChainVerifyResult()
    data class Broken(val deviceId: String, val atSeq: Long, val reason: String) : ChainVerifyResult()
    object Empty : ChainVerifyResult()
}

/**
 * Ordering is by a persisted per-device Lamport counter, never by wall clock — wall time is
 * user-settable and unsuitable as an ordering key (plan §6.7). One hash chain per device avoids
 * a false total order across devices that never actually coordinated.
 */
class Ledger(
    private val store: LedgerStore,
    private val clock: StrataClock,
    private val deviceId: String,
) {
    @Volatile
    private var lamport: Long = store.lastForDevice(deviceId)?.lamport ?: 0L

    @Synchronized
    fun append(type: LedgerEventType, payload: CanonicalValue): LedgerEvent {
        lamport += 1
        val prev = store.lastForDevice(deviceId)
        val prevHash = prev?.hash ?: GENESIS_HASH
        val seq = store.nextSeq()
        val wall = clock.wallClockMs()
        val mono = clock.monotonicMs()
        val hash = computeHash(prevHash, seq, deviceId, lamport, wall, mono, type, payload)
        val event = LedgerEvent(seq, deviceId, lamport, wall, mono, type, payload, prevHash, hash)
        store.appendRaw(event)
        return event
    }

    fun tail(n: Int): List<LedgerEvent> = store.allInOrder().takeLast(n)

    fun all(): List<LedgerEvent> = store.allInOrder()

    /** Recomputes every hash in every per-device chain and compares against the stored value. */
    fun verifyChain(): ChainVerifyResult {
        val byDevice = store.allInOrder().groupBy { it.deviceId }
        if (byDevice.isEmpty()) return ChainVerifyResult.Empty
        for ((dev, events) in byDevice) {
            val sorted = events.sortedBy { it.lamport }
            var expectedPrev = GENESIS_HASH
            for (e in sorted) {
                if (!e.prevHash.contentEquals(expectedPrev)) {
                    return ChainVerifyResult.Broken(dev, e.seq, "prevHash mismatch — chain tampered or reordered")
                }
                val recomputed = computeHash(e.prevHash, e.seq, e.deviceId, e.lamport, e.wallClockMs, e.monotonicMs, e.type, e.payload)
                if (!recomputed.contentEquals(e.hash)) {
                    return ChainVerifyResult.Broken(dev, e.seq, "hash mismatch — event content altered")
                }
                expectedPrev = e.hash
            }
        }
        return ChainVerifyResult.Valid
    }

    companion object {
        val GENESIS_HASH: ByteArray = ByteArray(32)

        private fun computeHash(
            prevHash: ByteArray, seq: Long, deviceId: String, lamport: Long,
            wallClockMs: Long, monotonicMs: Long, type: LedgerEventType, payload: CanonicalValue,
        ): ByteArray {
            val body = CanonicalValue.map(
                "seq" to CanonicalValue.of(seq),
                "deviceId" to CanonicalValue.of(deviceId),
                "lamport" to CanonicalValue.of(lamport),
                "wallClockMs" to CanonicalValue.of(wallClockMs),
                "monotonicMs" to CanonicalValue.of(monotonicMs),
                "type" to CanonicalValue.of(type.name),
                "payload" to payload,
            )
            val hashInput = prevHash + CanonicalCodec.encode(body)
            return MessageDigest.getInstance("SHA-256").digest(hashInput)
        }
    }
}
