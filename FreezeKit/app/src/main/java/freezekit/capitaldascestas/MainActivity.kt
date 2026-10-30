package freezekit.capitaldascestas

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var appVersionText: TextView
    private lateinit var androidVersionText: TextView
    private lateinit var deviceOwnerText: TextView
    private lateinit var efrpText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val basePadding = dp(28)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 245))
            setPadding(basePadding, basePadding, basePadding, basePadding)
        }

        val title = TextView(this).apply {
            text = getString(R.string.status_title)
            textSize = 26f
            setTextColor(Color.rgb(22, 60, 47))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title, rowLayoutParams(bottomMarginDp = 28))

        appVersionText = createInfoTextView()
        root.addView(appVersionText, rowLayoutParams(bottomMarginDp = 18))

        androidVersionText = createInfoTextView()
        root.addView(androidVersionText, rowLayoutParams(bottomMarginDp = 18))

        deviceOwnerText = createInfoTextView()
        root.addView(deviceOwnerText, rowLayoutParams(bottomMarginDp = 18))

        efrpText = createInfoTextView()
        root.addView(efrpText, rowLayoutParams(bottomMarginDp = 28))

        val updateButton = Button(this).apply {
            text = getString(R.string.refresh_button)
            isAllCaps = false
            setOnClickListener { updateStatus() }
        }
        root.addView(
            updateButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.setOnApplyWindowInsetsListener { view: View, insets: WindowInsets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(
                basePadding + systemBars.left,
                basePadding + systemBars.top,
                basePadding + systemBars.right,
                basePadding + systemBars.bottom
            )
            insets
        }

        setContentView(root)
        root.requestApplyInsets()
        updateStatus()
    }

    private fun updateStatus() {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)

        appVersionText.text =
            "${getString(R.string.app_version_label)}: ${getAppVersionName()}"
        androidVersionText.text =
            "${getString(R.string.android_version_label)}: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        deviceOwnerText.text =
            "${getString(R.string.device_owner_label)}: ${if (isDeviceOwner) "SIM" else "NÃO"}"
        efrpText.text = getEfrpStatus(devicePolicyManager, isDeviceOwner)
    }

    private fun getEfrpStatus(
        devicePolicyManager: DevicePolicyManager,
        isDeviceOwner: Boolean
    ): String {
        if (!isDeviceOwner) {
            return "${getString(R.string.efrp_label)}: verificar após tornar Device Owner"
        }

        return try {
            devicePolicyManager.getFactoryResetProtectionPolicy(
                ComponentName(this, AdminReceiver::class.java)
            )
            "${getString(R.string.efrp_label)}: SUPORTADA"
        } catch (exception: UnsupportedOperationException) {
            "${getString(R.string.efrp_label)}: NÃO SUPORTADA"
        } catch (exception: Exception) {
            "${getString(R.string.efrp_label)}: ERRO (${exception.javaClass.simpleName})"
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

    private fun createInfoTextView(): TextView = TextView(this).apply {
        textSize = 19f
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
