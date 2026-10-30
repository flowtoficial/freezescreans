package freezekit.capitaldascestas.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class SecretUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class RateLimitState(val retryAfterMillis: Long, val attemptsUntilLockout: Int)

/** Segredos TOTP cifrados e código de recuperação representado somente por salt/hash. */
class SecurityStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun isTotpConfigured(): Boolean =
        !preferences.getString(KEY_ACTIVE_IV, null).isNullOrBlank() &&
            !preferences.getString(KEY_ACTIVE_CIPHER, null).isNullOrBlank()

    fun hasPendingTotpSecret(): Boolean =
        !preferences.getString(KEY_PENDING_IV, null).isNullOrBlank() &&
            !preferences.getString(KEY_PENDING_CIPHER, null).isNullOrBlank()

    /** Verifica se os registros TOTP podem ser lidos; nunca altera nem descarta dados. */
    fun prepareForExplicitTotpReconfiguration(): Boolean {
        var active: ByteArray? = null
        var pending: ByteArray? = null
        return try {
            active = readEncryptedRecord(KEY_ACTIVE_IV, KEY_ACTIVE_CIPHER)
            pending = readEncryptedRecord(KEY_PENDING_IV, KEY_PENDING_CIPHER)
            true
        } catch (_: SecretUnavailableException) {
            false
        } finally {
            active?.fill(0)
            pending?.fill(0)
        }
    }

    /** Descarta somente após Recovery autenticado e confirmação explícita na interface. */
    fun discardUnreadableTotpRecordsAfterRecovery(userConfirmed: Boolean): Boolean {
        if (!userConfirmed || !AdminSession.isAuthenticatedWithRecovery()) return false
        if (prepareForExplicitTotpReconfiguration()) return false
        return try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) keyStore.deleteEntry(KEYSTORE_ALIAS)
            preferences.edit()
                .remove(KEY_ACTIVE_IV)
                .remove(KEY_ACTIVE_CIPHER)
                .remove(KEY_PENDING_IV)
                .remove(KEY_PENDING_CIPHER)
                .remove(KEY_PENDING_DISPLAYED)
                .remove(KEY_LAST_TOTP_COUNTER)
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun wasPendingSecretDisplayed(): Boolean = preferences.getBoolean(KEY_PENDING_DISPLAYED, false)

    fun generatePendingTotpSecret(replacePendingSecret: Boolean): String {
        if (hasPendingTotpSecret() && !replacePendingSecret) {
            throw IllegalStateException("A substituição do TOTP pendente exige confirmação")
        }
        val secret = ByteArray(TOTP_SECRET_BYTES)
        random.nextBytes(secret)
        return try {
            saveEncryptedRecord(KEY_PENDING_IV, KEY_PENDING_CIPHER, secret)
            if (!preferences.edit().putBoolean(KEY_PENDING_DISPLAYED, true).commit()) {
                throw IllegalStateException("Não foi possível salvar o estado de provisionamento")
            }
            Base32.encode(secret)
        } finally {
            secret.fill(0)
        }
    }

    fun saveExistingPendingTotpSecret(base32: String, replacePendingSecret: Boolean): Boolean {
        if (hasPendingTotpSecret() && !replacePendingSecret) return false
        val secret = Base32.decode(base32) ?: return false
        if (secret.size != TOTP_SECRET_BYTES) {
            secret.fill(0)
            return false
        }
        return try {
            saveEncryptedRecord(KEY_PENDING_IV, KEY_PENDING_CIPHER, secret)
            preferences.edit().putBoolean(KEY_PENDING_DISPLAYED, false).commit()
        } finally {
            secret.fill(0)
        }
    }

    fun confirmPendingTotp(code: String): Boolean {
        // Não permite que a promoção do pendente sobrescreva um ativo ilegível.
        readEncryptedRecord(KEY_ACTIVE_IV, KEY_ACTIVE_CIPHER)?.fill(0)
        val secret = readEncryptedRecord(KEY_PENDING_IV, KEY_PENDING_CIPHER) ?: return false
        return try {
            val counter = Totp.matchingCounter(secret, code) ?: return false
            val iv = preferences.getString(KEY_PENDING_IV, null) ?: return false
            val cipher = preferences.getString(KEY_PENDING_CIPHER, null) ?: return false
            preferences.edit()
                .putString(KEY_ACTIVE_IV, iv)
                .putString(KEY_ACTIVE_CIPHER, cipher)
                .putLong(KEY_LAST_TOTP_COUNTER, counter)
                .remove(KEY_PENDING_IV)
                .remove(KEY_PENDING_CIPHER)
                .remove(KEY_PENDING_DISPLAYED)
                .commit()
        } finally {
            secret.fill(0)
        }
    }

    fun verifyActiveTotp(code: String): Boolean {
        val secret = readEncryptedRecord(KEY_ACTIVE_IV, KEY_ACTIVE_CIPHER) ?: return false
        return try {
            val lastAccepted = preferences.getLong(KEY_LAST_TOTP_COUNTER, -1L)
            val matchedCounter = Totp.matchingCounter(secret, code, lastAcceptedCounter = lastAccepted)
                ?: return false
            preferences.edit().putLong(KEY_LAST_TOTP_COUNTER, matchedCounter).commit()
        } finally {
            secret.fill(0)
        }
    }

    fun hasRecoveryCode(): Boolean =
        !preferences.getString(KEY_RECOVERY_SALT, null).isNullOrBlank() &&
            !preferences.getString(KEY_RECOVERY_HASH, null).isNullOrBlank()

    /** Gera e persiste apenas salt/hash. O texto puro é retornado uma vez para exibição. */
    fun generateRecoveryCode(replaceExistingCode: Boolean): String? {
        if (hasRecoveryCode() && !replaceExistingCode) return null
        val raw = CharArray(RECOVERY_CODE_LENGTH) {
            RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]
        }
        return try {
            if (!storeRecoveryHash(raw)) return null
            String(raw).chunked(4).joinToString(" ")
        } finally {
            raw.fill('\u0000')
        }
    }

    fun saveExistingRecoveryCode(input: String, replaceExistingCode: Boolean): Boolean {
        if (hasRecoveryCode() && !replaceExistingCode) return false
        val canonical = normalizeRecoveryCode(input) ?: return false
        val chars = canonical.toCharArray()
        return try {
            storeRecoveryHash(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun verifyRecoveryCode(input: String): Boolean {
        val canonical = normalizeRecoveryCode(input) ?: return false
        val saltText = preferences.getString(KEY_RECOVERY_SALT, null) ?: return false
        val hashText = preferences.getString(KEY_RECOVERY_HASH, null) ?: return false
        val salt = decodeBase64OrNull(saltText) ?: return false
        val storedHash = decodeBase64OrNull(hashText)
        if (storedHash == null) {
            salt.fill(0)
            return false
        }
        if (salt.size != RECOVERY_SALT_BYTES || storedHash.size != RECOVERY_HASH_BYTES) {
            salt.fill(0)
            storedHash.fill(0)
            return false
        }

        val chars = canonical.toCharArray()
        val candidate = try {
            deriveRecoveryHash(chars, salt)
        } catch (_: Exception) {
            salt.fill(0)
            storedHash.fill(0)
            return false
        } finally {
            chars.fill('\u0000')
        }
        return try {
            MessageDigest.isEqual(storedHash, candidate)
        } finally {
            salt.fill(0)
            storedHash.fill(0)
            candidate.fill(0)
        }
    }

    fun rateLimitState(nowMillis: Long = System.currentTimeMillis()): RateLimitState {
        val deadline = preferences.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val remaining = if (deadline > nowMillis) deadline - nowMillis else 0L
        val failures = preferences.getInt(KEY_FAILURES, 0).coerceAtLeast(0)
        val level = preferences.getInt(KEY_LOCKOUT_LEVEL, 0).coerceAtLeast(0)
        val remainingAttempts = if (level > 0) 1 else (INITIAL_FAILURE_LIMIT - failures).coerceAtLeast(0)
        return RateLimitState(remaining, remainingAttempts)
    }

    /** TOTP e recuperação usam o mesmo contador e deadline persistente. */
    fun recordFailedAuthentication(nowMillis: Long = System.currentTimeMillis()): RateLimitState =
        synchronized(LOCKOUT_MUTEX) {
            val deadline = preferences.getLong(KEY_LOCKOUT_UNTIL, 0L)
            if (deadline > nowMillis) return@synchronized rateLimitState(nowMillis)

            val level = preferences.getInt(KEY_LOCKOUT_LEVEL, 0).coerceAtLeast(0)
            val failures = preferences.getInt(KEY_FAILURES, 0).coerceAtLeast(0)
            val editor = preferences.edit()
            if (level == 0 && failures + 1 < INITIAL_FAILURE_LIMIT) {
                editor.putInt(KEY_FAILURES, failures + 1)
                if (!editor.commit()) return@synchronized RateLimitState(Long.MAX_VALUE, 0)
                return@synchronized rateLimitState(nowMillis)
            }

            val nextLevel = if (level == 0) 1 else if (level == Int.MAX_VALUE) Int.MAX_VALUE else level + 1
            val duration = lockoutDurationMillis(nextLevel)
            val until = saturatedAdd(nowMillis, duration)
            editor.putInt(KEY_LOCKOUT_LEVEL, nextLevel)
                .putInt(KEY_FAILURES, 0)
                .putLong(KEY_LOCKOUT_UNTIL, until)
            if (!editor.commit()) return@synchronized RateLimitState(Long.MAX_VALUE, 0)
            rateLimitState(nowMillis)
        }

    fun recordSuccessfulAuthentication(): Boolean = synchronized(LOCKOUT_MUTEX) {
        preferences.edit()
            .putInt(KEY_FAILURES, 0)
            .putInt(KEY_LOCKOUT_LEVEL, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
    }

    fun isFirstRunComplete(): Boolean = preferences.getBoolean(KEY_FIRST_RUN_COMPLETE, false)

    fun markFirstRunComplete(): Boolean =
        preferences.edit().putBoolean(KEY_FIRST_RUN_COMPLETE, true).commit()

    fun wizardStep(): String = preferences.getString(KEY_WIZARD_STEP, STEP_TOTP) ?: STEP_TOTP

    fun setWizardStep(step: String): Boolean =
        preferences.edit().putString(KEY_WIZARD_STEP, step).commit()

    fun reconfigurationStep(): String = preferences.getString(KEY_RECONFIG_STEP, STEP_TOTP) ?: STEP_TOTP

    fun setReconfigurationStep(step: String): Boolean =
        preferences.edit().putString(KEY_RECONFIG_STEP, step).commit()

    fun clearReconfigurationStep(): Boolean =
        preferences.edit().remove(KEY_RECONFIG_STEP).commit()

    fun isConferTestPassed(): Boolean = preferences.getBoolean(KEY_CONFER_TEST_PASSED, false)

    fun setConferTestPassed(passed: Boolean): Boolean =
        preferences.edit().putBoolean(KEY_CONFER_TEST_PASSED, passed).commit()

    private fun storeRecoveryHash(password: CharArray): Boolean {
        val salt = ByteArray(RECOVERY_SALT_BYTES).also(random::nextBytes)
        val hash = try {
            deriveRecoveryHash(password, salt)
        } catch (_: Exception) {
            salt.fill(0)
            return false
        }
        return try {
            preferences.edit()
                .putString(KEY_RECOVERY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_RECOVERY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .commit()
        } finally {
            salt.fill(0)
            hash.fill(0)
        }
    }

    private fun deriveRecoveryHash(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, RECOVERY_HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
                ?: throw GeneralSecurityException("Derivação PBKDF2 sem bytes")
        } finally {
            spec.clearPassword()
        }
    }

    private fun normalizeRecoveryCode(input: String): String? {
        val canonical = input
            .filterNot { it == ' ' || it == '-' || it == '\n' || it == '\r' || it == '\t' }
            .uppercase(Locale.ROOT)
        if (canonical.length != RECOVERY_CODE_LENGTH || canonical.any { it !in RECOVERY_ALPHABET }) {
            return null
        }
        return canonical
    }

    private fun saveEncryptedRecord(ivKey: String, cipherKey: String, plaintext: ByteArray) {
        try {
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv ?: throw GeneralSecurityException("IV AES/GCM indisponível")
            if (iv.size != GCM_IV_BYTES) {
                iv.fill(0)
                throw GeneralSecurityException("Tamanho de IV AES/GCM inesperado")
            }
            val encrypted = cipher.doFinal(plaintext)
            val saved = preferences.edit()
                .putString(ivKey, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(cipherKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit()
            iv.fill(0)
            encrypted.fill(0)
            if (!saved) throw GeneralSecurityException("Falha ao persistir o segredo cifrado")
        } catch (error: Exception) {
            throw SecretUnavailableException("Não foi possível proteger o segredo TOTP", error)
        }
    }

    private fun readEncryptedRecord(ivKey: String, cipherKey: String): ByteArray? {
        val ivText = preferences.getString(ivKey, null)
        val cipherText = preferences.getString(cipherKey, null)
        if (ivText == null && cipherText == null) return null
        if (ivText.isNullOrBlank() || cipherText.isNullOrBlank()) {
            throw SecretUnavailableException("Registro TOTP incompleto")
        }

        val iv = decodeBase64OrNull(ivText)
            ?: throw SecretUnavailableException("IV TOTP inválido")
        val encrypted = decodeBase64OrNull(cipherText)
            ?: throw SecretUnavailableException("Registro TOTP inválido")
        if (iv.size != GCM_IV_BYTES || encrypted.size <= GCM_TAG_BYTES) {
            iv.fill(0)
            encrypted.fill(0)
            throw SecretUnavailableException("Registro TOTP inválido")
        }

        return try {
            val key = getExistingEncryptionKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encrypted).also { plaintext ->
                if (plaintext.size != TOTP_SECRET_BYTES) {
                    plaintext.fill(0)
                    throw SecretUnavailableException("Tamanho de segredo TOTP inválido")
                }
            }
        } catch (error: SecretUnavailableException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw SecretUnavailableException("Autenticação do segredo TOTP falhou", error)
        } catch (error: Exception) {
            throw SecretUnavailableException("Não foi possível ler o segredo TOTP", error)
        } finally {
            iv.fill(0)
            encrypted.fill(0)
        }
    }

    private fun getExistingEncryptionKey(): SecretKey {
        val keyStore = loadKeyStore()
        val key = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            ?: throw SecretUnavailableException("Chave TOTP do Android Keystore indisponível")
        return key
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = loadKeyStore()
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        if (hasAnyEncryptedSecret()) {
            throw SecretUnavailableException("Chave TOTP ausente; é necessária reconfiguração")
        }

        return try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(AES_KEY_BITS)
                    .build()
            )
            generator.generateKey()
        } catch (error: Exception) {
            throw SecretUnavailableException("Não foi possível criar a chave TOTP", error)
        }
    }

    private fun loadKeyStore(): KeyStore = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (error: Exception) {
        throw SecretUnavailableException("Android Keystore indisponível", error)
    }

    private fun hasAnyEncryptedSecret(): Boolean =
        !preferences.getString(KEY_ACTIVE_CIPHER, null).isNullOrBlank() ||
            !preferences.getString(KEY_PENDING_CIPHER, null).isNullOrBlank()

    private fun decodeBase64OrNull(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun lockoutDurationMillis(level: Int): Long {
        var duration = FIRST_LOCKOUT_MILLIS
        repeat((level - 1).coerceIn(0, 63)) {
            if (duration > Long.MAX_VALUE / 2L) return Long.MAX_VALUE
            duration *= 2L
        }
        return duration
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (right == Long.MAX_VALUE || left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    companion object {
        const val STEP_TOTP = "TOTP"
        const val STEP_RECOVERY = "RECOVERY"
        const val STEP_PASSWORD = "PASSWORD"
        const val STEP_CONFER = "CONFER"
        const val STEP_FINALIZE = "FINALIZE"

        private const val PREFERENCES_NAME = "freezekit_security_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "freezekit_totp_aes_gcm_v1"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16
        private const val TOTP_SECRET_BYTES = 20
        private const val PBKDF2_ITERATIONS = 100_000
        private const val RECOVERY_HASH_BYTES = 32
        private const val RECOVERY_SALT_BYTES = 16
        private const val RECOVERY_CODE_LENGTH = 16
        private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val INITIAL_FAILURE_LIMIT = 5
        private const val FIRST_LOCKOUT_MILLIS = 30_000L

        private const val KEY_ACTIVE_IV = "totp_active_iv"
        private const val KEY_ACTIVE_CIPHER = "totp_active_ciphertext"
        private const val KEY_PENDING_IV = "totp_pending_iv"
        private const val KEY_PENDING_CIPHER = "totp_pending_ciphertext"
        private const val KEY_PENDING_DISPLAYED = "totp_pending_displayed"
        private const val KEY_LAST_TOTP_COUNTER = "totp_last_accepted_counter"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_pbkdf2_hash"
        private const val KEY_FAILURES = "auth_consecutive_failures"
        private const val KEY_LOCKOUT_LEVEL = "auth_lockout_level"
        private const val KEY_LOCKOUT_UNTIL = "auth_lockout_until_epoch_ms"
        private const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        private const val KEY_WIZARD_STEP = "wizard_step"
        private const val KEY_RECONFIG_STEP = "reconfiguration_step"
        private const val KEY_CONFER_TEST_PASSED = "confer_test_passed"
        private val LOCKOUT_MUTEX = Any()
    }
}
