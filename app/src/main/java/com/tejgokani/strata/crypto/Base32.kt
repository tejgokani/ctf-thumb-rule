package com.tejgokani.strata.crypto

/** RFC 4648 base32 (no padding), used for the human-transcribable recovery key. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0L
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                val index = ((buffer ushr bitsLeft) and 0x1F).toInt()
                sb.append(ALPHABET[index])
            }
        }
        if (bitsLeft > 0) {
            val index = ((buffer shl (5 - bitsLeft)) and 0x1F).toInt()
            sb.append(ALPHABET[index])
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.uppercase().filter { it != '-' && it != ' ' }
        var buffer = 0L
        var bitsLeft = 0
        val out = ArrayList<Byte>((clean.length * 5) / 8 + 1)
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "invalid base32 character: $c" }
            buffer = (buffer shl 5) or value.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer ushr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
