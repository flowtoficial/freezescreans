package freezekit.capitaldascestas

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import freezekit.capitaldascestas.config.BrowserConfig
import freezekit.capitaldascestas.kiosk.KioskManager
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.log.EventType
import freezekit.capitaldascestas.security.AdminSession
import freezekit.capitaldascestas.security.AdminSessionActivity
import freezekit.capitaldascestas.security.SecurityStore
import freezekit.capitaldascestas.system.NativeHomeLauncher
import freezekit.capitaldascestas.system.SystemSettingsLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminActivity : AdminSessionActivity() {
    private lateinit var statusView: TextView
    private lateinit var sessionView: TextView
    private lateinit var timeView: TextView
    private lateinit var config: BrowserConfig
    private lateinit var initialUrlField: EditText
    private lateinit var allowedHostsField: EditText
    private lateinit var cameraHostsField: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val refreshStatus = object : Runnable {
        override fun run() {
            if (!isFinishing && ::statusView.isInitialized) {
                refreshClockAndCountdown()
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!adminSessionValidAtCreate) return
        config = BrowserConfig(this)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(packageName)) {
            Toast.makeText(this, "A tela de administrador exige Device Owner.", Toast.LENGTH_LONG).show()
            expireAdminSession()
            return
        }

        val page = UiSupport.page(this, "Administrador", "Acesso temporário autenticado. A sessão encerra após 10 minutos sem toque ou quando a tela apaga.")
        statusView = UiSupport.label(this, "", 14f)
        page.body.addView(statusView, UiSupport.matchWrap(this, 8))
        timeView = UiSupport.label(this, "", 14f)
        page.body.addView(timeView, UiSupport.matchWrap(this, 4))
        sessionView = UiSupport.label(this, "", 15f, bold = true)
        page.body.addView(sessionView, UiSupport.matchWrap(this, 18))
        UiSupport.addLabel(page.body, this, "Configuração do navegador", 19f, true)

        initialUrlField = UiSupport.addInput(
            page.body,
            this,
            "URL inicial HTTPS",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        ).apply { setText(config.initialUrl) }
        allowedHostsField = UiSupport.addInput(
            page.body,
            this,
            "Hosts permitidos (um por linha)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        ).apply { setText(config.allowedHosts.sorted().joinToString("\n")); setSingleLine(false); minLines = 2 }
        cameraHostsField = UiSupport.addInput(
            page.body,
            this,
            "Hosts com câmera (um por linha)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        ).apply { setText(config.cameraHosts.sorted().joinToString("\n")); setSingleLine(false); minLines = 2 }
        UiSupport.addButton(page.body, this, "Validar e salvar endereços") { saveBrowserConfiguration() }

        UiSupport.addLabel(page.body, this, "Acesso e ferramentas", 19f, true)
        UiSupport.addButton(page.body, this, "Reconfigurar TOTP ou recuperação") {
            startActivity(
                Intent(this, SetupWizardActivity::class.java)
                    .putExtra(SetupWizardActivity.EXTRA_RECONFIGURE, true)
            )
        }
        UiSupport.addButton(page.body, this, "Limpar cookies, armazenamento e cache do navegador") {
            MainActivity.clearActiveWebSession(this) { cleared ->
                EventLog.record(
                    this,
                    EventType.BROWSER_SESSION_CLEARED,
                    "result=${if (cleared) "success" else "unavailable"}"
                )
                Toast.makeText(
                    this,
                    if (cleared) "Dados de sessão do navegador limpos." else "Não foi possível acessar a WebView ativa.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        UiSupport.addButton(page.body, this, "Abrir configurações de Wi-Fi") {
            val result = SystemSettingsLauncher.openWifi(this)
            if (result.launched) EventLog.record(this, EventType.WIFI_SETTINGS_OPENED, "source=admin")
            showLaunchResult(result)
        }
        UiSupport.addButton(page.body, this, "Abrir tela de senha Android") {
            val result = SystemSettingsLauncher.openAndroidPassword(this)
            if (result.launched) EventLog.record(this, EventType.PASSWORD_SETTINGS_OPENED, "source=admin")
            showLaunchResult(result)
        }
        UiSupport.addButton(page.body, this, "Ver registro de eventos") {
            startActivity(Intent(this, EventLogActivity::class.java))
        }

        UiSupport.addLabel(page.body, this, "Quiosque", 19f, true)
        UiSupport.addButton(page.body, this, "Entrar no quiosque") {
            val kiosk = KioskManager(this)
            if (kiosk.entrarNoQuiosque(reason = "administrator")) {
                AdminSession.end(this, "manual_exit")
                finish()
            } else {
                Toast.makeText(this, "Não foi possível ativar Lock Task.", Toast.LENGTH_LONG).show()
            }
        }
        UiSupport.addButton(page.body, this, "Sair do quiosque e abrir a tela inicial Android") {
            val kiosk = KioskManager(this)
            if (!kiosk.sairDoQuiosque(reason = "administrator")) {
                Toast.makeText(this, "Não foi possível sair do quiosque.", Toast.LENGTH_LONG).show()
                return@addButton
            }
            AdminSession.end(this, "manual_exit")
            if (NativeHomeLauncher.open(this)) finish()
            else Toast.makeText(this, "Tela inicial do Android não encontrada.", Toast.LENGTH_LONG).show()
        }
        UiSupport.addButton(page.body, this, "Voltar ao CONFER") {
            AdminSession.end(this, "manual_exit")
            finish()
        }

        UiSupport.addLabel(page.body, this, "Etapas futuras", 19f, true)
        UiSupport.addButton(page.body, this, "Modo manutenção (Etapa 6 — ainda não implementado)") {}.apply {
            isEnabled = false
            alpha = 0.55f
        }

        setContentView(page.scroll)
        refreshStatusText()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            if (::statusView.isInitialized) refreshStatusText()
            if (::initialUrlField.isInitialized) initialUrlField.setText(BrowserConfig(this).initialUrl)
            if (::allowedHostsField.isInitialized) {
                allowedHostsField.setText(BrowserConfig(this).allowedHosts.sorted().joinToString("\n"))
            }
            if (::cameraHostsField.isInitialized) {
                cameraHostsField.setText(BrowserConfig(this).cameraHosts.sorted().joinToString("\n"))
            }
            handler.removeCallbacks(refreshStatus)
            handler.post(refreshStatus)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshStatus)
        super.onDestroy()
    }

    private fun saveBrowserConfiguration() {
        val oldInitialUrl = config.initialUrl
        val oldAllowedHosts = config.allowedHosts
        val oldCameraHosts = config.cameraHosts
        val error = config.saveConfiguration(
            initialUrlField.text.toString(),
            allowedHostsField.text.toString(),
            cameraHostsField.text.toString()
        )
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }
        val updatedConfig = BrowserConfig(this)
        if (oldInitialUrl != updatedConfig.initialUrl) {
            EventLog.record(
                this,
                EventType.CONFIG_INITIAL_ADDRESS_CHANGED,
                "host=${EventLog.hostOnly(updatedConfig.initialUrl)}"
            )
        }
        if (oldAllowedHosts != updatedConfig.allowedHosts) {
            EventLog.record(
                this,
                EventType.CONFIG_ALLOWED_HOSTS_CHANGED,
                "hosts=${updatedConfig.allowedHosts.sorted().joinToString(",").ifEmpty { "none" }}"
            )
        }
        if (oldCameraHosts != updatedConfig.cameraHosts) {
            EventLog.record(
                this,
                EventType.CONFIG_CAMERA_HOSTS_CHANGED,
                "hosts=${updatedConfig.cameraHosts.sorted().joinToString(",").ifEmpty { "none" }}"
            )
        }
        config = updatedConfig
        MainActivity.reloadConfiguredStartPage()
        Toast.makeText(this, "Configuração validada e salva.", Toast.LENGTH_LONG).show()
        refreshStatusText()
    }

    private fun showLaunchResult(result: SystemSettingsLauncher.LaunchResult) {
        if (!result.launched) {
            Toast.makeText(this, result.message ?: "Não foi possível abrir as configurações.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatusText() {
        if (!::statusView.isInitialized) return
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val owner = dpm.isDeviceOwnerApp(packageName)
        val kiosk = KioskManager(this)
        val version = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "desconhecida"
        } catch (_: Exception) {
            "indisponível"
        }
        val lockTaskState = try {
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState
        } catch (_: Exception) {
            ActivityManager.LOCK_TASK_MODE_NONE
        }
        val lockTaskLabel = when (lockTaskState) {
            ActivityManager.LOCK_TASK_MODE_LOCKED -> "Lock Task ativo"
            ActivityManager.LOCK_TASK_MODE_PINNED -> "fixação comum (não é Lock Task gerenciado)"
            else -> if (kiosk.isKioskEnabled()) "configurado, mas inativo" else "inativo"
        }
        val efrp = readEfrpStatus(dpm, owner)
        statusView.text = "Versão do app: $version\n" +
            "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
            "Device Owner: ${if (owner) "SIM" else "NÃO"}\n" +
            "EFRP (somente leitura): $efrp\n" +
            "Quiosque: $lockTaskLabel"
        refreshClockAndCountdown()
    }

    private fun refreshClockAndCountdown() {
        if (!::timeView.isInitialized || !::sessionView.isInitialized) return
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        timeView.text = "Data e hora do tablet: $now"
        val remaining = AdminSession.remainingMillis().coerceAtLeast(0L)
        val seconds = remaining / 1_000L + if (remaining % 1_000L == 0L) 0L else 1L
        sessionView.text = String.format(Locale.ROOT, "Sessão expira em %02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun readEfrpStatus(dpm: DevicePolicyManager, owner: Boolean): String {
        if (!owner) return "verificar após tornar Device Owner"
        return try {
            val policy = dpm.getFactoryResetProtectionPolicy(
                android.content.ComponentName(this, AdminReceiver::class.java)
            )
            when {
                policy == null -> "sem política configurada"
                policy.isFactoryResetProtectionEnabled -> "ativa"
                else -> "inativa"
            }
        } catch (_: UnsupportedOperationException) {
            "não suportada"
        } catch (error: Exception) {
            "ERRO (${error.javaClass.simpleName})"
        }
    }
}
