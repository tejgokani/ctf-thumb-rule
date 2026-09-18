package com.tejgokani.strata.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Deterministic, self-describing binary encoding used for ledger events and manifest records.
 *
 * Why not JSON: JSON object key ordering is not guaranteed across library versions/platforms,
 * and the ledger's hash chain (Ledger.kt) depends on byte-identical re-serialization of the same
 * logical event. CanonicalValue.Map sorts its entries by key bytes before encoding, so the same
 * logical content always produces the same bytes.
 */
sealed class CanonicalValue {
    object Null : CanonicalValue()
    data class Bool(val v: Boolean) : CanonicalValue()
    data class Long_(val v: Long) : CanonicalValue()
    data class Str(val v: String) : CanonicalValue()
    data class Bytes(val v: ByteArray) : CanonicalValue()
    data class List_(val v: List<CanonicalValue>) : CanonicalValue()
    data class Map_(val v: Map<String, CanonicalValue>) : CanonicalValue()

    companion object {
        fun of(v: Long) = Long_(v)
        fun of(v: Int) = Long_(v.toLong())
        fun of(v: String) = Str(v)
        fun of(v: Boolean) = Bool(v)
        fun of(v: ByteArray) = Bytes(v)
        fun map(vararg pairs: Pair<String, CanonicalValue>) = Map_(pairs.toMap())
    }
}

object CanonicalCodec {
    private const val TAG_NULL: Byte = 0x00
    private const val TAG_BOOL: Byte = 0x01
    private const val TAG_LONG: Byte = 0x02
    private const val TAG_STR: Byte = 0x03
    private const val TAG_BYTES: Byte = 0x04
    private const val TAG_LIST: Byte = 0x05
    private const val TAG_MAP: Byte = 0x06

    fun encode(value: CanonicalValue): ByteArray {
        val out = ByteArrayOutputStream()
        write(out, value)
        return out.toByteArray()
    }

    private fun write(out: ByteArrayOutputStream, value: CanonicalValue) {
        when (value) {
            is CanonicalValue.Null -> out.write(byteArrayOf(TAG_NULL))
            is CanonicalValue.Bool -> out.write(byteArrayOf(TAG_BOOL, if (value.v) 1 else 0))
            is CanonicalValue.Long_ -> {
                out.write(byteArrayOf(TAG_LONG))
                out.write(ByteBuffer.allocate(8).putLong(value.v).array())
            }
            is CanonicalValue.Str -> {
                val bytes = value.v.toByteArray(StandardCharsets.UTF_8)
                out.write(byteArrayOf(TAG_STR))
                writeLenPrefixed(out, bytes)
            }
            is CanonicalValue.Bytes -> {
                out.write(byteArrayOf(TAG_BYTES))
                writeLenPrefixed(out, value.v)
            }
            is CanonicalValue.List_ -> {
                out.write(byteArrayOf(TAG_LIST))
                out.write(ByteBuffer.allocate(4).putInt(value.v.size).array())
                value.v.forEach { write(out, it) }
            }
            is CanonicalValue.Map_ -> {
                out.write(byteArrayOf(TAG_MAP))
                val sorted = value.v.entries.sortedBy { it.key }
                out.write(ByteBuffer.allocate(4).putInt(sorted.size).array())
                for ((k, v) in sorted) {
                    writeLenPrefixed(out, k.toByteArray(StandardCharsets.UTF_8))
                    write(out, v)
                }
            }
        }
    }

    private fun writeLenPrefixed(out: ByteArrayOutputStream, bytes: ByteArray) {
        out.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
        out.write(bytes)
    }

    fun decode(bytes: ByteArray): CanonicalValue {
        val buf = ByteBuffer.wrap(bytes)
        return readValue(buf)
    }

    private fun readValue(buf: ByteBuffer): CanonicalValue {
        return when (val tag = buf.get()) {
            TAG_NULL -> CanonicalValue.Null
            TAG_BOOL -> CanonicalValue.Bool(buf.get() != 0.toByte())
            TAG_LONG -> CanonicalValue.Long_(buf.long)
            TAG_STR -> CanonicalValue.Str(String(readLenPrefixed(buf), StandardCharsets.UTF_8))
            TAG_BYTES -> CanonicalValue.Bytes(readLenPrefixed(buf))
            TAG_LIST -> {
                val n = buf.int
                CanonicalValue.List_((0 until n).map { readValue(buf) })
            }
            TAG_MAP -> {
                val n = buf.int
                val map = LinkedHashMap<String, CanonicalValue>()
                repeat(n) {
                    val k = String(readLenPrefixed(buf), StandardCharsets.UTF_8)
                    map[k] = readValue(buf)
                }
                CanonicalValue.Map_(map)
            }
            else -> throw IllegalArgumentException("Unknown canonical tag: $tag")
        }
    }

    private fun readLenPrefixed(buf: ByteBuffer): ByteArray {
        val len = buf.int
        val out = ByteArray(len)
        buf.get(out)
        return out
    }
}

// Convenience accessors for reading a decoded Map_ back into typed values.
fun CanonicalValue.asMap(): Map<String, CanonicalValue> = (this as CanonicalValue.Map_).v
fun CanonicalValue.asString(): String = (this as CanonicalValue.Str).v
fun CanonicalValue.asLong(): Long = (this as CanonicalValue.Long_).v
fun CanonicalValue.asBool(): Boolean = (this as CanonicalValue.Bool).v
fun CanonicalValue.asBytes(): ByteArray = (this as CanonicalValue.Bytes).v
