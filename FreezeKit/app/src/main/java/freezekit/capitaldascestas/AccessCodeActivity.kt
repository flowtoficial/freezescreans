package freezekit.capitaldascestas

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.log.EventType
import freezekit.capitaldascestas.security.AdminSession
import freezekit.capitaldascestas.security.SecurityStore
import freezekit.capitaldascestas.security.SecretUnavailableException
import java.util.Locale

class AccessCodeActivity : Activity() {
    private lateinit var store: SecurityStore
    private lateinit var codeField: EditText
    private lateinit var statusText: TextView
    private var recoveryMode = false
    private val handler = Handler(Looper.getMainLooper())
    private val countdown = object : Runnable {
        override fun run() {
            refreshLockoutText()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecurityStore(this)
        if (!store.isFirstRunComplete()) {
            Toast.makeText(this, "Conclua primeiro a configuração inicial.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!store.isTotpConfigured()) recoveryMode = true
        val page = UiSupport.page(this, "Acesso do administrador", "Digite o código do autenticador.")
        statusText = UiSupport.label(this, "", 14f)
        page.body.addView(statusText, UiSupport.matchWrap(this, 8))
        codeField = UiSupport.addInput(
            page.body,
            this,
            "Código de 6 dígitos",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            6
        )
        UiSupport.addButton(page.body, this, "Validar código") { verifyCode() }
        UiSupport.addButton(page.body, this, "Usar código de recuperação") {
            recoveryMode = !recoveryMode
            codeField.setText("")
            updateMode()
        }
        UiSupport.addButton(page.body, this, "Cancelar e voltar ao CONFER") { finish() }
        setContentView(page.scroll)
        updateMode()
        if (!store.isTotpConfigured()) {
            statusText.text = "TOTP indisponível. Use o código de recuperação para reconfigurar o acesso."
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(countdown)
        handler.post(countdown)
    }

    override fun onPause() {
        handler.removeCallbacks(countdown)
        super.onPause()
    }

    private fun updateMode() {
        if (recoveryMode) {
            codeField.hint = "Código de recuperação (16 caracteres)"
            codeField.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            codeField.filters = arrayOf(android.text.InputFilter.LengthFilter(19))
        } else {
            codeField.hint = "Código de 6 dígitos"
            codeField.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            codeField.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        refreshLockoutText()
    }

    private fun verifyCode() {
        val state = store.rateLimitState()
        if (state.retryAfterMillis > 0L) {
            refreshLockoutText()
            return
        }

        val accepted = if (recoveryMode) {
            store.verifyRecoveryCode(codeField.text.toString())
        } else {
            try {
                store.verifyActiveTotp(codeField.text.toString())
            } catch (_: SecretUnavailableException) {
                codeField.setText("")
                recoveryMode = true
                updateMode()
                statusText.text = "Segredo TOTP indisponível. Use o código de recuperação para reconfigurar o acesso."
                return
            }
        }

        if (accepted) {
            EventLog.record(
                this,
                if (recoveryMode) EventType.AUTH_RECOVERY_CORRECT else EventType.AUTH_TOTP_CORRECT,
                "source=access"
            )
            if (!store.recordSuccessfulAuthentication()) {
                statusText.text = "Não foi possível confirmar o armazenamento seguro. Tente novamente."
                return
            }
            AdminSession.start(recoveryAuthenticated = recoveryMode)
            EventLog.record(
                this,
                EventType.ADMIN_SESSION_STARTED,
                "method=${if (recoveryMode) "recovery" else "totp"}"
            )
            startActivity(android.content.Intent(this, AdminActivity::class.java))
            finish()
            return
        }

        codeField.setText("")
        EventLog.record(
            this,
            if (recoveryMode) EventType.AUTH_RECOVERY_INCORRECT else EventType.AUTH_TOTP_INCORRECT,
            "source=access"
        )
        val updated = store.recordFailedAuthentication()
        if (updated.retryAfterMillis > 0L) {
            EventLog.record(
                this,
                EventType.AUTH_LOCKOUT_STARTED,
                "duration=${ceilSeconds(updated.retryAfterMillis)}s"
            )
        }
        statusText.text = if (updated.retryAfterMillis > 0L) {
            "Aguarde para tentar novamente."
        } else {
            "Código incorreto. Restam ${updated.attemptsUntilLockout} tentativa(s) antes da espera."
        }
        refreshLockoutText()
    }

    private fun refreshLockoutText() {
        if (!::statusText.isInitialized || !::store.isInitialized) return
        val state = store.rateLimitState()
        if (state.retryAfterMillis > 0L) {
            val seconds = ceilSeconds(state.retryAfterMillis)
            val minutes = seconds / 60L
            val remainder = seconds % 60L
            statusText.text = if (minutes > 0L) {
                String.format(Locale.ROOT, "Tentativas bloqueadas. Aguarde %d min %02d s.", minutes, remainder)
            } else {
                "Tentativas bloqueadas. Aguarde $seconds s."
            }
        } else if (statusText.text.isNullOrBlank() || statusText.text.toString().startsWith("Tentativas bloqueadas")) {
            statusText.text = "Tentativas restantes antes da espera: ${state.attemptsUntilLockout}."
        }
    }

    private fun ceilSeconds(millis: Long): Long =
        (millis / 1_000L + if (millis % 1_000L == 0L) 0L else 1L).coerceAtLeast(1L)
}
