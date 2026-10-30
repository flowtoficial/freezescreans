package freezekit.capitaldascestas.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Implementação TOTP/HOTP própria conforme RFC 6238/4226, sem biblioteca TOTP externa. */
object Totp {
    const val PERIOD_SECONDS = 30L
    const val DIGITS = 6
    private const val MODULUS = 1_000_000

    fun codeForCounter(secret: ByteArray, counter: Long): String {
        require(secret.isNotEmpty()) { "Segredo vazio" }
        require(counter >= 0L) { "Contador inválido" }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val digest = mac.doFinal(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(counter).array())
        val offset = digest[digest.lastIndex].toInt() and 0x0f
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or
            ((digest[offset + 1].toInt() and 0xff) shl 16) or
            ((digest[offset + 2].toInt() and 0xff) shl 8) or
            (digest[offset + 3].toInt() and 0xff)
        return String.format(Locale.ROOT, "%06d", binary % MODULUS)
    }

    fun currentCounter(nowMillis: Long = System.currentTimeMillis()): Long =
        Math.floorDiv(Math.floorDiv(nowMillis, 1_000L), PERIOD_SECONDS)

    /** Retorna o passo correspondente ao código; -1, 0 e +1 são os únicos aceitos. */
    fun matchingCounter(
        secret: ByteArray,
        suppliedCode: String,
        nowMillis: Long = System.currentTimeMillis(),
        lastAcceptedCounter: Long = -1L
    ): Long? {
        if (!suppliedCode.matches(Regex("[0-9]{6}"))) return null
        val current = currentCounter(nowMillis)
        val supplied = suppliedCode.toByteArray(Charsets.US_ASCII)
        var newestMatch: Long? = null

        for (counter in longArrayOf(current - 1L, current, current + 1L)) {
            if (counter < 0L || counter <= lastAcceptedCounter) continue
            val expected = codeForCounter(secret, counter).toByteArray(Charsets.US_ASCII)
            if (MessageDigest.isEqual(expected, supplied) &&
                (newestMatch == null || counter > newestMatch)
            ) {
                newestMatch = counter
            }
        }
        return newestMatch
    }
}
