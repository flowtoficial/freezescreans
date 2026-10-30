package freezekit.capitaldascestas.security

import java.util.Locale

/** RFC 4648 Base32 para segredos do app; padding é omitido. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val lookup = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { index, character -> this[character.code] = index }
    }

    fun encode(data: ByteArray): String {
        val output = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsInBuffer = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsInBuffer += 8
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5
                output.append(ALPHABET[(buffer shr bitsInBuffer) and 0x1f])
            }
            buffer = if (bitsInBuffer == 0) 0 else buffer and ((1 shl bitsInBuffer) - 1)
        }
        if (bitsInBuffer > 0) {
            output.append(ALPHABET[(buffer shl (5 - bitsInBuffer)) and 0x1f])
        }
        return output.toString()
    }

    /** Aceita espaços/hífens de apresentação, mas rejeita padding e bits residuais não canônicos. */
    fun decode(value: String): ByteArray? {
        val normalized = value
            .filterNot { it == ' ' || it == '-' || it == '\n' || it == '\r' || it == '\t' }
            .uppercase(Locale.ROOT)
        if (normalized.isEmpty() || normalized.any { it.code >= lookup.size || lookup[it.code] < 0 }) {
            return null
        }

        val output = ByteArray(normalized.length * 5 / 8)
        var outputIndex = 0
        var buffer = 0
        var bitsInBuffer = 0
        for (character in normalized) {
            buffer = (buffer shl 5) or lookup[character.code]
            bitsInBuffer += 5
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8
                output[outputIndex++] = ((buffer shr bitsInBuffer) and 0xff).toByte()
            }
            buffer = if (bitsInBuffer == 0) 0 else buffer and ((1 shl bitsInBuffer) - 1)
        }

        if (bitsInBuffer > 0 && buffer != 0) return null
        return output.copyOf(outputIndex)
    }
}
