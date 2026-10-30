package freezekit.capitaldascestas.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import freezekit.capitaldascestas.kiosk.KioskManager

/** Base para telas da sessão administrativa e para o controle do Back do assistente inicial. */
abstract class AdminSessionActivity : Activity() {
    protected open val requiresAdminSession: Boolean = true
    protected var adminSessionValidAtCreate: Boolean = true
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var backCallback: OnBackInvokedCallback? = null
    private var receiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) expireAdminSession()
        }
    }
    private val timeoutRunnable = Runnable { expireAdminSession() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (requiresAdminSession) {
            if (!AdminSession.isActive()) {
                adminSessionValidAtCreate = false
                finish()
                return
            }
            registerScreenOffReceiver()
            scheduleTimeout()
        }
        registerBackCallback()
    }

    override fun onResume() {
        super.onResume()
        if (requiresAdminSession && adminSessionValidAtCreate) {
            if (!AdminSession.isActive()) {
                expireAdminSession()
                return
            }
            KioskManager(this).restoreLockTaskPackagesIfNeeded()
            scheduleTimeout()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (requiresAdminSession && adminSessionValidAtCreate &&
            event.actionMasked == MotionEvent.ACTION_DOWN
        ) {
            AdminSession.registerTouch()
            if (!AdminSession.isActive()) {
                expireAdminSession()
                return false
            }
            scheduleTimeout()
        }
        return super.dispatchTouchEvent(event)
    }

    @Deprecated("Voltar é roteado por OnBackInvokedDispatcher nas versões atuais do Android")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleBack()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
        super.onDestroy()
    }

    protected open fun onAdminBack() {
        if (requiresAdminSession) expireAdminSession() else finish()
    }

    protected fun expireAdminSession() {
        if (requiresAdminSession) {
            AdminSession.clear()
            KioskManager(this).restoreLockTaskPackagesIfNeeded()
        }
        if (!isFinishing) finish()
    }

    private fun scheduleTimeout() {
        handler.removeCallbacks(timeoutRunnable)
        val remaining = AdminSession.remainingMillis()
        if (remaining <= 0L) expireAdminSession()
        else handler.postDelayed(timeoutRunnable, remaining)
    }

    private fun registerScreenOffReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenOffReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback { handleBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backCallback = callback
    }

    private fun handleBack() {
        onAdminBack()
    }
}
