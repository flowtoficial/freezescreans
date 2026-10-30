package freezekit.capitaldascestas.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import freezekit.capitaldascestas.AdminReceiver
import freezekit.capitaldascestas.MainActivity
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.log.EventType
import java.lang.ref.WeakReference

class KioskManager(private val activity: Activity) {
    private val devicePolicyManager =
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(activity, AdminReceiver::class.java)
    private val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(activity.packageName)

    /** Valor persistente usado por MainActivity.onResume; ausente significa ativado (compatibilidade). */
    fun isKioskEnabled(): Boolean = preferences.getBoolean(KEY_KIOSK_ENABLED, true)

    fun setKioskEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(KEY_KIOSK_ENABLED, enabled).commit()

    fun entrarNoQuiosque(reason: String? = null): Boolean {
        if (!setKioskEnabled(true)) return false
        if (!isDeviceOwner()) return false

        return try {
            devicePolicyManager.setLockTaskPackages(
                adminComponent,
                arrayOf(activity.packageName)
            )
            if (!devicePolicyManager.isLockTaskPermitted(activity.packageName)) return false

            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            devicePolicyManager.addPersistentPreferredActivity(
                adminComponent,
                homeFilter,
                ComponentName(activity, MainActivity::class.java)
            )
            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
            )
            devicePolicyManager.setStatusBarDisabled(adminComponent, true)

            if (activity is MainActivity) {
                activity.startLockTask()
                lockTaskStarter = WeakReference(activity)
            } else {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                )
            }
            if (reason == "administrator" || reason == "assistant") {
                val eventReason = if (reason == "administrator") "administrador" else "assistente"
                EventLog.record(activity, EventType.KIOSK_ENTERED, "reason=$eventReason")
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun sairDoQuiosque(reason: String = "administrator"): Boolean {
        val stateSaved = setKioskEnabled(false)
        if (!isDeviceOwner()) return false

        return try {
            // stopLockTask é efetivo na Activity que originalmente iniciou o Lock Task.
            val starter = lockTaskStarter?.get()
            if (starter != null) starter.stopLockTask() else activity.stopLockTask()
            lockTaskStarter = null
            devicePolicyManager.setStatusBarDisabled(adminComponent, false)
            devicePolicyManager.clearPackagePersistentPreferredActivities(
                adminComponent,
                activity.packageName
            )
            devicePolicyManager.setLockTaskPackages(adminComponent, emptyArray<String>())
            if (stateSaved && (reason == "administrator" || reason == "assistant")) {
                val eventReason = if (reason == "administrator") "administrador" else "assistente"
                EventLog.record(activity, EventType.KIOSK_EXITED, "reason=$eventReason")
            }
            stateSaved
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** Permite por curto período o pacote resolvido do Settings; conserva o FreezeKit. */
    fun temporarilyAllowLockTaskPackage(externalPackage: String): Boolean {
        if (!isDeviceOwner() || externalPackage.isBlank() || externalPackage == activity.packageName) {
            return false
        }
        val current = devicePolicyManager.getLockTaskPackages(adminComponent).toMutableSet()
        val expanded = (current + activity.packageName + externalPackage).distinct().toTypedArray()
        if (!preferences.edit().putBoolean(KEY_RESTORE_LOCK_TASK, true).commit()) return false

        return try {
            devicePolicyManager.setLockTaskPackages(adminComponent, expanded)
            if (devicePolicyManager.isLockTaskPermitted(externalPackage)) {
                true
            } else {
                restoreLockTaskPackagesIfNeeded()
                false
            }
        } catch (_: SecurityException) {
            restoreLockTaskPackagesIfNeeded()
            false
        } catch (_: IllegalStateException) {
            restoreLockTaskPackagesIfNeeded()
            false
        }
    }

    /** Chamado no retorno ao FreezeKit e ao expirar a sessão; deixa somente o app na allowlist. */
    fun restoreLockTaskPackagesIfNeeded(): Boolean {
        if (!preferences.getBoolean(KEY_RESTORE_LOCK_TASK, false)) return false
        if (!isDeviceOwner()) {
            preferences.edit().remove(KEY_RESTORE_LOCK_TASK).commit()
            return false
        }
        return try {
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(activity.packageName))
            preferences.edit().remove(KEY_RESTORE_LOCK_TASK).commit()
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "freezekit_kiosk_state"
        const val KEY_KIOSK_ENABLED = "quiosque_ativado"
        const val KEY_RESTORE_LOCK_TASK = "restore_lock_task_packages"
        var lockTaskStarter: WeakReference<MainActivity>? = null
    }
}
