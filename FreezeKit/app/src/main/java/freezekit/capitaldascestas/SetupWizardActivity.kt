package freezekit.capitaldascestas

import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import freezekit.capitaldascestas.kiosk.KioskManager
import freezekit.capitaldascestas.security.AdminSession
import freezekit.capitaldascestas.security.AdminSessionActivity
import freezekit.capitaldascestas.security.QrCodeRenderer
import freezekit.capitaldascestas.security.SecurityStore
import freezekit.capitaldascestas.security.SecretUnavailableException

class SetupWizardActivity : AdminSessionActivity() {
    override val requiresAdminSession: Boolean
        get() = intent.getBooleanExtra(EXTRA_RECONFIGURE, false)

    private lateinit var store: SecurityStore
    private val reconfigure: Boolean
        get() = intent.getBooleanExtra(EXTRA_RECONFIGURE, false)
    private lateinit var body: android.widget.LinearLayout
    private var qrImage: ImageView? = null
    private var secretText: TextView? = null
    private var stepMessage: TextView? = null
    private var secretField: android.widget.EditText? = null
    private var codeField: android.widget.EditText? = null
    private var recoveryField: android.widget.EditText? = null
    private var recoveryDisplay: TextView? = null
    private var discardUnreadableTotpButton: android.widget.Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (reconfigure && !adminSessionValidAtCreate) return
        store = SecurityStore(this)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(packageName)) {
            Toast.makeText(this, "A configuração requer Device Owner.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (reconfigure && !store.prepareForExplicitTotpReconfiguration() &&
            !store.setReconfigurationStep(SecurityStore.STEP_TOTP)
        ) {
            Toast.makeText(this, "O TOTP não pode ser lido e não foi possível retomar a recuperação com segurança.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!reconfigure && store.isFirstRunComplete()) {
            if (store.isTotpConfigured()) {
                finish()
                return
            }
        }
        if (!reconfigure && !store.isTotpConfigured()) {
            store.setWizardStep(SecurityStore.STEP_TOTP)
        }

        val savedStep = currentStep()
        if (!reconfigure && savedStep == STEP_EFRP_RESERVED) {
            // Slot reservado para a etapa futura de EFRP; nenhuma tela ou API EFRP é executada aqui.
            saveStep(SecurityStore.STEP_PASSWORD)
        } else if (!reconfigure && savedStep == SecurityStore.STEP_TOTP && store.isTotpConfigured()) {
            saveStep(SecurityStore.STEP_RECOVERY)
        }
        renderCurrentStep()
    }

    override fun onResume() {
        super.onResume()
        KioskManager(this).restoreLockTaskPackagesIfNeeded()
        if (isFinishing || !::store.isInitialized) return
        if (currentStep() == SecurityStore.STEP_PASSWORD && isAndroidDeviceSecure()) {
            moveTo(SecurityStore.STEP_CONFER)
        }
    }

    override fun onAdminBack() {
        if (reconfigure) {
            finish()
        } else {
            Toast.makeText(this, "Conclua o assistente para ativar o modo administrador.", Toast.LENGTH_LONG).show()
        }
    }

    private fun currentStep(): String =
        if (reconfigure) store.reconfigurationStep() else store.wizardStep()

    private fun saveStep(step: String): Boolean =
        if (reconfigure) store.setReconfigurationStep(step) else store.setWizardStep(step)

    private fun moveTo(step: String) {
        if (!saveStep(step)) {
            Toast.makeText(this, "Não foi possível salvar o progresso. Tente novamente.", Toast.LENGTH_LONG).show()
            return
        }
        renderCurrentStep()
    }

    private fun renderCurrentStep() {
        qrImage = null
        secretText = null
        stepMessage = null
        secretField = null
        codeField = null
        recoveryField = null
        recoveryDisplay = null

        val step = currentStep()
        when (step) {
            SecurityStore.STEP_TOTP -> renderTotpStep()
            SecurityStore.STEP_RECOVERY -> renderRecoveryStep()
            SecurityStore.STEP_PASSWORD -> renderPasswordStep()
            SecurityStore.STEP_CONFER -> renderConferStep()
            SecurityStore.STEP_FINALIZE -> renderFinalizeStep()
            STEP_EFRP_RESERVED -> {
                if (reconfigure) finish() else moveTo(SecurityStore.STEP_PASSWORD)
            }
            else -> {
                if (reconfigure) finish() else moveTo(SecurityStore.STEP_TOTP)
            }
        }
    }

    private fun makePage(title: String, subtitle: String, progress: String): android.widget.LinearLayout {
        val page = UiSupport.page(this, title, "$progress\n$subtitle")
        body = page.body
        setContentView(page.scroll)
        return body
    }

    private fun renderTotpStep() {
        makePage(
            "1 de 5 — Configurar TOTP",
            "Use HMAC-SHA1, 6 dígitos e 30 segundos no autenticador. Gere um segredo novo neste tablet ou digite o segredo compartilhado existente.",
            progressLabel(1)
        )
        val recordsReadable = store.prepareForExplicitTotpReconfiguration()
        val message = UiSupport.label(
            this,
            if (recordsReadable) "O segredo e o QR são exibidos apenas quando você pedir para gerar um novo."
            else "O registro TOTP não pôde ser lido. Volte ao CONFER e entre usando Recovery antes de reconfigurar.",
            14f
        )
        stepMessage = message
        body.addView(message, UiSupport.matchWrap(this, 8))

        val image = ImageView(this).apply {
            visibility = View.GONE
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        qrImage = image
        body.addView(image, UiSupport.matchWrap(this, 8))
        val secret = UiSupport.label(this, "", 18f, bold = true)
        secretText = secret
        body.addView(secret, UiSupport.matchWrap(this, 8))

        UiSupport.addButton(body, this, "Gerar novo segredo e QR") { requestGenerateTotpSecret() }
        val secretInput = UiSupport.addInput(
            body,
            this,
            "Segredo Base32 existente (20 bytes)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            40
        )
        secretField = secretInput
        UiSupport.addButton(body, this, "Usar segredo existente") {
            val value = secretInput.text.toString()
            if (!store.prepareForExplicitTotpReconfiguration()) {
                showUnreadableTotpMessage(message)
            } else if (store.hasPendingTotpSecret()) {
                confirmPendingTotpReplacement {
                    saveExistingPendingSecret(value, secretInput, message, replacePendingSecret = true)
                }
            } else {
                saveExistingPendingSecret(value, secretInput, message, replacePendingSecret = false)
            }
        }
        discardUnreadableTotpButton = UiSupport.addButton(
            body,
            this,
            "Descartar TOTP ilegível após Recovery"
        ) { confirmDiscardUnreadableTotp(message) }
        discardUnreadableTotpButton?.visibility = if (
            reconfigure && !recordsReadable && AdminSession.isAuthenticatedWithRecovery()
        ) View.VISIBLE else View.GONE

        val code = UiSupport.addInput(
            body,
            this,
            "Código atual de 6 dígitos",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            6
        )
        codeField = code
        UiSupport.addButton(body, this, "Confirmar TOTP") { confirmTotp(code.text.toString(), message) }
        val keepTotpButton = UiSupport.addButton(body, this, "Manter TOTP atual e alterar Recovery") {
            if (!store.prepareForExplicitTotpReconfiguration()) {
                showUnreadableTotpMessage(message)
            } else if (!store.isTotpConfigured()) {
                message.text = "Não há TOTP ativo para manter. Configure um segredo antes de continuar."
            } else if (store.hasPendingTotpSecret()) {
                message.text = "Há um TOTP pendente. Confirme-o ou substitua-o antes de manter o TOTP atual."
            } else {
                moveTo(SecurityStore.STEP_RECOVERY)
            }
        }
        keepTotpButton.visibility = if (
            reconfigure && recordsReadable && store.isTotpConfigured() && !store.hasPendingTotpSecret()
        ) View.VISIBLE else View.GONE
        UiSupport.addButton(body, this, "Cancelar reconfiguração") {
            if (reconfigure) finish() else Toast.makeText(this, "O assistente não pode ser pulado.", Toast.LENGTH_SHORT).show()
        }.visibility = if (reconfigure) View.VISIBLE else View.GONE

        if (recordsReadable && store.hasPendingTotpSecret()) {
            message.text = if (store.wasPendingSecretDisplayed()) {
                "O QR/segredo deste candidato já foi exibido uma vez. Digite o código do autenticador; se não salvou, gere outro segredo."
            } else {
                "Segredo existente armazenado de forma protegida. Digite o código atual do autenticador."
            }
        }
    }

    private fun requestGenerateTotpSecret() {
        if (!store.prepareForExplicitTotpReconfiguration()) {
            stepMessage?.let(::showUnreadableTotpMessage)
            return
        }
        if (store.hasPendingTotpSecret()) {
            confirmPendingTotpReplacement { generateTotpSecret(replacePendingSecret = true) }
        } else {
            generateTotpSecret(replacePendingSecret = false)
        }
    }

    private fun generateTotpSecret(replacePendingSecret: Boolean) {
        try {
            if (!store.prepareForExplicitTotpReconfiguration()) {
                stepMessage?.let(::showUnreadableTotpMessage)
                return
            }
            val base32 = store.generatePendingTotpSecret(replacePendingSecret)
            val uri = "otpauth://totp/FreezeKit:CapitalDasCestas?secret=$base32&issuer=FreezeKit&algorithm=SHA1&digits=6&period=30"
            val bitmap: Bitmap = QrCodeRenderer.render(uri)
            qrImage?.setImageBitmap(bitmap)
            qrImage?.visibility = View.VISIBLE
            secretText?.text = "Segredo (guarde no cofre):\n$base32"
            stepMessage?.text = "Escaneie o QR no autenticador. O texto é exibido somente agora; depois confirme um código atual."
        } catch (_: SecretUnavailableException) {
            stepMessage?.let(::showUnreadableTotpMessage)
        } catch (_: Exception) {
            stepMessage?.text = "Não foi possível gerar o QR/segredo. Nenhum dado foi registrado em log."
        }
    }

    private fun showUnreadableTotpMessage(message: TextView) {
        if (reconfigure && AdminSession.isAuthenticatedWithRecovery()) {
            discardUnreadableTotpButton?.visibility = View.VISIBLE
            message.text = "O TOTP antigo não pôde ser lido. Você entrou por Recovery; confirme abaixo se deseja descartar o registro local e cadastrar outro."
        } else {
            message.text = "O TOTP antigo não pôde ser lido. Volte ao CONFER, entre usando Recovery e então reconfigure o acesso. Nenhum registro foi apagado."
        }
    }

    private fun confirmDiscardUnreadableTotp(message: TextView) {
        if (!reconfigure || !AdminSession.isAuthenticatedWithRecovery()) {
            showUnreadableTotpMessage(message)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Descartar TOTP ilegível?")
            .setMessage("Esta ação remove a chave local do Keystore e os registros TOTP ativo/pendente. Só prossiga se você acabou de autenticar com o código de recuperação. O código de recuperação não será alterado.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Descartar e continuar") { _, _ ->
                if (store.discardUnreadableTotpRecordsAfterRecovery(userConfirmed = true)) {
                    discardUnreadableTotpButton?.visibility = View.GONE
                    message.text = "Registro TOTP ilegível descartado após Recovery. Agora cadastre ou digite um novo segredo."
                } else {
                    message.text = "Nenhum registro foi removido. O Keystore pode ter voltado a funcionar; tente novamente ou saia sem alterar os dados."
                }
            }
            .show()
    }

    private fun confirmPendingTotpReplacement(onConfirmed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Substituir TOTP pendente?")
            .setMessage("Isto substitui somente o candidato TOTP ainda não confirmado. O TOTP ativo continua protegido até o novo código ser confirmado.")
            .setNegativeButton("Manter candidato", null)
            .setPositiveButton("Substituir candidato") { _, _ -> onConfirmed() }
            .show()
    }

    private fun saveExistingPendingSecret(
        value: String,
        field: android.widget.EditText,
        message: TextView,
        replacePendingSecret: Boolean
    ) {
        try {
            if (!store.prepareForExplicitTotpReconfiguration()) {
                showUnreadableTotpMessage(message)
            } else if (store.saveExistingPendingTotpSecret(value, replacePendingSecret)) {
                field.setText("")
                message.text = "Segredo protegido no Android Keystore. Insira o código atual do autenticador para confirmar."
            } else {
                message.text = "O segredo precisa ser Base32 válido e decodificar para exatamente 20 bytes."
            }
        } catch (_: SecretUnavailableException) {
            showUnreadableTotpMessage(message)
        }
    }

    private fun confirmTotp(code: String, message: TextView) {
        val lockout = store.rateLimitState()
        if (lockout.retryAfterMillis > 0L) {
            message.text = "Aguarde ${ceilSeconds(lockout.retryAfterMillis)} s antes de tentar novamente."
            return
        }
        try {
            if (!store.prepareForExplicitTotpReconfiguration()) {
                showUnreadableTotpMessage(message)
                return
            }
            if (!store.confirmPendingTotp(code)) {
                val updated = store.recordFailedAuthentication()
                message.text = if (updated.retryAfterMillis > 0L) {
                    "Código incorreto. Aguarde ${ceilSeconds(updated.retryAfterMillis)} s."
                } else {
                    "Código incorreto. Restam ${updated.attemptsUntilLockout} tentativa(s) antes da espera."
                }
                return
            }
            if (!store.recordSuccessfulAuthentication()) {
                message.text = "Não foi possível atualizar a proteção contra tentativas. Tente novamente."
                return
            }
            moveTo(SecurityStore.STEP_RECOVERY)
        } catch (_: SecretUnavailableException) {
            showUnreadableTotpMessage(message)
        }
    }

    private fun renderRecoveryStep() {
        makePage(
            "2 de 5 — Código de recuperação",
            "Gere um código novo para salvar no cofre ou digite o código compartilhado existente. O app guarda somente salt e hash PBKDF2; o texto não é recuperável depois.",
            progressLabel(2)
        )
        val message = UiSupport.label(this, "", 14f)
        recoveryDisplay = message
        body.addView(message, UiSupport.matchWrap(this, 12))
        UiSupport.addButton(body, this, "Gerar novo código (mostrar uma vez)") {
            if (store.hasRecoveryCode()) {
                confirmRecoveryReplacement { generateRecoveryCode(message, replaceExistingCode = true) }
            } else {
                generateRecoveryCode(message, replaceExistingCode = false)
            }
        }
        val field = UiSupport.addInput(
            body,
            this,
            "Código existente: 16 caracteres",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            19
        )
        recoveryField = field
        UiSupport.addButton(body, this, "Usar código existente") {
            val value = field.text.toString()
            if (store.hasRecoveryCode()) {
                confirmRecoveryReplacement { saveExistingRecoveryCode(value, field, message, replaceExistingCode = true) }
            } else {
                saveExistingRecoveryCode(value, field, message, replaceExistingCode = false)
            }
        }
        UiSupport.addButton(body, this, if (reconfigure) "Concluir reconfiguração" else "Continuar") {
            if (!store.hasRecoveryCode()) {
                message.text = "Configure o código de recuperação antes de continuar."
            } else if (reconfigure) {
                store.clearReconfigurationStep()
                finish()
            } else {
                store.setConferTestPassed(false)
                moveTo(SecurityStore.STEP_PASSWORD)
            }
        }
        if (store.hasRecoveryCode()) {
            message.text = "Já existe um hash de recuperação. Você pode continuar ou substituí-lo."
        }
    }

    private fun confirmRecoveryReplacement(onConfirmed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Substituir código de recuperação?")
            .setMessage("O código antigo deixará de funcionar. O novo código será mostrado uma única vez; confirme que você pode salvá-lo no cofre antes de continuar.")
            .setNegativeButton("Manter código atual", null)
            .setPositiveButton("Substituir código") { _, _ -> onConfirmed() }
            .show()
    }

    private fun generateRecoveryCode(message: TextView, replaceExistingCode: Boolean) {
        val generated = store.generateRecoveryCode(replaceExistingCode)
        if (generated == null) {
            message.text = "Não foi possível salvar o hash do código. Tente novamente."
        } else {
            message.text = "Salve agora; o tablet guardou somente o hash:\n\n$generated"
        }
    }

    private fun saveExistingRecoveryCode(
        value: String,
        field: android.widget.EditText,
        message: TextView,
        replaceExistingCode: Boolean
    ) {
        if (store.saveExistingRecoveryCode(value, replaceExistingCode)) {
            field.setText("")
            message.text = "Código configurado. O texto não foi guardado no tablet."
        } else {
            message.text = "Formato inválido. Use 16 caracteres A-Z (sem I/O) e 2-9."
        }
    }

    private fun renderPasswordStep() {
        makePage(
            "3 de 5 — Senha do Android",
            "Defina uma senha, PIN ou padrão seguro para este tablet. Esta etapa não aplica políticas M5 de complexidade.",
            progressLabel(3)
        )
        val message = UiSupport.label(this, "", 14f)
        stepMessage = message
        body.addView(message, UiSupport.matchWrap(this, 8))
        UiSupport.addButton(body, this, "Abrir tela para definir senha Android") {
            val result = freezekit.capitaldascestas.system.SystemSettingsLauncher.openAndroidPassword(this)
            if (!result.launched) message.text = result.message ?: "Não foi possível abrir a tela de senha."
        }
        UiSupport.addButton(body, this, "Já está configurada — continuar") {
            if (isAndroidDeviceSecure()) moveTo(SecurityStore.STEP_CONFER)
            else message.text = "O Android ainda não informa PIN, padrão ou senha segura configurada."
        }
        UiSupport.addLabel(body, this, "Ao voltar da tela do sistema, o assistente verifica novamente se a tela de bloqueio está segura.", 13f)
    }

    private fun renderConferStep() {
        makePage(
            "4 de 5 — Testar o CONFER",
            "Abra o navegador real do FreezeKit, teste o CONFER e depois escolha Funcionou ou Não funcionou.",
            progressLabel(4)
        )
        val message = UiSupport.label(this, "", 14f)
        stepMessage = message
        body.addView(message, UiSupport.matchWrap(this, 8))
        UiSupport.addButton(body, this, "Abrir teste do CONFER") {
            startActivity(MainActivity.createSetupTestIntent(this))
        }
        if (store.isConferTestPassed()) {
            message.text = "Teste marcado como Funcionou. Você pode avançar."
        } else {
            message.text = "Depois do teste, escolha uma resposta na faixa exibida sobre o CONFER."
        }
        UiSupport.addButton(body, this, "Continuar após Funcionou") {
            if (store.isConferTestPassed()) moveTo(SecurityStore.STEP_FINALIZE)
            else message.text = "Marque Funcionou na tela do CONFER antes de continuar."
        }
        UiSupport.addButton(body, this, "Reiniciar o teste") { store.setConferTestPassed(false) }
    }

    private fun renderFinalizeStep() {
        makePage(
            "5 de 5 — Ativar bloqueios e quiosque",
            "O assistente está pronto. Ao concluir, o app ativa o quiosque existente e retorna ao CONFER. Nenhuma política M5 ou EFRP é aplicada nesta etapa.",
            progressLabel(5)
        )
        val checks = listOf(
            "TOTP: ${if (store.isTotpConfigured()) "configurado" else "pendente"}",
            "Recuperação: ${if (store.hasRecoveryCode()) "configurada" else "pendente"}",
            "Senha Android: ${if (isAndroidDeviceSecure()) "configurada" else "pendente"}",
            "Teste CONFER: ${if (store.isConferTestPassed()) "Funcionou" else "pendente"}"
        )
        UiSupport.addLabel(body, this, checks.joinToString("\n"), 15f)
        val message = UiSupport.label(this, "", 14f)
        body.addView(message, UiSupport.matchWrap(this, 8))
        UiSupport.addButton(body, this, "Concluir e entrar no quiosque") {
            if (!store.isTotpConfigured() || !store.hasRecoveryCode() ||
                !isAndroidDeviceSecure() || !store.isConferTestPassed()
            ) {
                message.text = "Há etapas pendentes; volte ao passo correspondente antes de concluir."
                return@addButton
            }
            val kiosk = KioskManager(this)
            if (!kiosk.entrarNoQuiosque()) {
                message.text = "Não foi possível ativar o quiosque. Confira Device Owner e Lock Task."
                return@addButton
            }
            if (!store.markFirstRunComplete()) {
                message.text = "O quiosque foi ativado, mas não foi possível salvar a conclusão. Tente novamente."
                return@addButton
            }
            finish()
        }
    }

    private fun progressLabel(step: Int): String =
        if (reconfigure) "Reconfigurar acesso · $step de 2" else "Assistente inicial · $step de 5"

    private fun ceilSeconds(millis: Long): Long =
        (millis / 1_000L + if (millis % 1_000L == 0L) 0L else 1L).coerceAtLeast(1L)

    private fun isAndroidDeviceSecure(): Boolean {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isDeviceSecure
    }

    companion object {
        const val EXTRA_RECONFIGURE = "freezekit.extra.RECONFIGURE_ACCESS"
        private const val STEP_EFRP_RESERVED = "EFRP_RESERVED"
    }
}
