package freezekit.capitaldascestas

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import freezekit.capitaldascestas.kiosk.KioskManager

class StatusActivity : Activity() {
    private lateinit var appVersionText: TextView
    private lateinit var androidVersionText: TextView
    private lateinit var deviceOwnerText: TextView
    private lateinit var efrpText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(244, 247, 245))
        }

        val title = TextView(this).apply {
            text = "Painel de status (provisório)"
            textSize = 24f
            setTextColor(Color.rgb(22, 60, 47))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title, rowLayoutParams(24))

        appVersionText = createInfoTextView()
        root.addView(appVersionText, rowLayoutParams(14))

        androidVersionText = createInfoTextView()
        root.addView(androidVersionText, rowLayoutParams(14))

        deviceOwnerText = createInfoTextView()
        root.addView(deviceOwnerText, rowLayoutParams(14))

        efrpText = createInfoTextView()
        root.addView(efrpText, rowLayoutParams(24))

        val updateButton = Button(this).apply {
            text = getString(R.string.refresh_button)
            isAllCaps = false
            setOnClickListener { updateStatus() }
        }
        root.addView(updateButton, rowLayoutParams(16))

        val googleTestButton = Button(this).apply {
            text = getString(R.string.test_google_button)
            isAllCaps = false
            setOnClickListener {
                requestMainActivityNavigation("https://www.google.com")
            }
        }
        root.addView(googleTestButton, rowLayoutParams(12))

        val subdomainTestButton = Button(this).apply {
            text = getString(R.string.test_subdomain_button)
            isAllCaps = false
            setOnClickListener {
                requestMainActivityNavigation("https://teste.manus.space")
            }
        }
        root.addView(subdomainTestButton, rowLayoutParams(0))

        val enterKioskButton = Button(this).apply {
            text = getString(R.string.enter_kiosk_dev_button)
            isAllCaps = false
            setOnClickListener { enterKioskForDevelopment() }
        }
        root.addView(enterKioskButton, rowLayoutParams(12))

        val exitKioskButton = Button(this).apply {
            text = getString(R.string.exit_kiosk_dev_button)
            isAllCaps = false
            setOnClickListener { exitKioskForDevelopment() }
        }
        root.addView(exitKioskButton, rowLayoutParams(0))

        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::appVersionText.isInitialized) {
            updateStatus()
        }
    }

    private fun updateStatus() {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)

        appVersionText.text =
            "Versão do app: ${getAppVersionName()}"
        androidVersionText.text =
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        deviceOwnerText.text =
            "Device Owner: ${if (isDeviceOwner) "SIM" else "NÃO"}"
        efrpText.text = getEfrpStatus(devicePolicyManager, isDeviceOwner)
    }

    private fun getEfrpStatus(
        devicePolicyManager: DevicePolicyManager,
        isDeviceOwner: Boolean
    ): String {
        if (!isDeviceOwner) {
            return "EFRP: verificar após tornar Device Owner"
        }

        return try {
            devicePolicyManager.getFactoryResetProtectionPolicy(
                ComponentName(this, AdminReceiver::class.java)
            )
            "EFRP: SUPORTADA"
        } catch (exception: UnsupportedOperationException) {
            "EFRP: NÃO SUPORTADA"
        } catch (exception: Exception) {
            "EFRP: ERRO (${exception.javaClass.simpleName})"
        }
    }

    private fun getAppVersionName(): String {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.versionName ?: "desconhecida"
    }

    private fun requestMainActivityNavigation(url: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_REQUEST_URL
            putExtra(MainActivity.EXTRA_REQUESTED_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun enterKioskForDevelopment() {
        if (!KioskManager(this).entrarNoQuiosque()) {
            Toast.makeText(
                this,
                getString(R.string.kiosk_requires_device_owner),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun exitKioskForDevelopment() {
        if (!KioskManager(this).sairDoQuiosque()) {
            Toast.makeText(
                this,
                getString(R.string.kiosk_requires_device_owner),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val homeActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val systemHome = homeActivities.firstOrNull {
            it.activityInfo?.packageName?.let { activityPackage -> activityPackage != packageName } == true
        }?.activityInfo

        if (systemHome == null) {
            Toast.makeText(
                this,
                "Tela inicial do Android não encontrada",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val explicitHomeIntent = Intent(homeIntent).apply {
            component = ComponentName(systemHome.packageName, systemHome.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(explicitHomeIntent)
        finish()
    }

    private fun createInfoTextView(): TextView = TextView(this).apply {
        textSize = 18f
        setTextColor(Color.rgb(35, 45, 40))
    }

    private fun rowLayoutParams(bottomMarginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(bottomMarginDp)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
