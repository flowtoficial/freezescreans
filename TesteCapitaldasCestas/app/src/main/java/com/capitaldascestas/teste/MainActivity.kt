package com.capitaldascestas.teste

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.ComponentName
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
    private lateinit var androidVersionText: TextView
    private lateinit var deviceOwnerText: TextView
    private lateinit var efrpSupportText: TextView

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
            text = "Olá, Capital das Cestas"
            textSize = 26f
            setTextColor(Color.rgb(22, 60, 47))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title, rowLayoutParams(bottomMarginDp = 28))

        androidVersionText = createInfoTextView()
        root.addView(androidVersionText, rowLayoutParams(bottomMarginDp = 18))

        deviceOwnerText = createInfoTextView()
        root.addView(deviceOwnerText, rowLayoutParams(bottomMarginDp = 18))

        efrpSupportText = createInfoTextView()
        root.addView(efrpSupportText, rowLayoutParams(bottomMarginDp = 28))

        val updateButton = Button(this).apply {
            text = "Atualizar"
            isAllCaps = false
            setOnClickListener { updateInformation() }
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
        updateInformation()
    }

    private fun updateInformation() {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        androidVersionText.text =
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)
        deviceOwnerText.text = "Device Owner: ${if (isDeviceOwner) "SIM" else "NÃO"}"

        efrpSupportText.text = if (!isDeviceOwner) {
            "Suporte à EFRP: verificar após tornar Device Owner"
        } else {
            try {
                devicePolicyManager.getFactoryResetProtectionPolicy(
                    ComponentName(this, AdminReceiver::class.java)
                )
                "Suporte à EFRP: SIM"
            } catch (exception: UnsupportedOperationException) {
                "Suporte à EFRP: NÃO"
            } catch (exception: Exception) {
                "Suporte à EFRP: ERRO (${exception::class.java.simpleName})"
            }
        }
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
