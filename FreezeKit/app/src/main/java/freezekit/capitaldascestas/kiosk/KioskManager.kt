package freezekit.capitaldascestas.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import freezekit.capitaldascestas.AdminReceiver
import freezekit.capitaldascestas.MainActivity
import java.lang.ref.WeakReference

class KioskManager(private val activity: Activity) {
    private val devicePolicyManager =
        activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(activity, AdminReceiver::class.java)
    private val preferences =
        activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isKioskEnabled(): Boolean = preferences.getBoolean(KEY_KIOSK_ACTIVATED, true)

    fun entrarNoQuiosque(): Boolean {
        if (!saveKioskEnabled(true)) {
            return false
        }
        if (!devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
            return false
        }

        devicePolicyManager.setLockTaskPackages(
            adminComponent,
            arrayOf(activity.packageName)
        )
        if (!devicePolicyManager.isLockTaskPermitted(activity.packageName)) {
            return false
        }

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
        return true
    }

    fun sairDoQuiosque(): Boolean {
        if (!saveKioskEnabled(false)) {
            return false
        }
        if (!devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
            return false
        }

        // stopLockTask is effective only from the activity that started Lock Task.
        val starter = lockTaskStarter?.get()
        if (starter != null) {
            starter.stopLockTask()
        } else {
            activity.stopLockTask()
        }
        lockTaskStarter = null
        devicePolicyManager.setStatusBarDisabled(adminComponent, false)
        devicePolicyManager.clearPackagePersistentPreferredActivities(
            adminComponent,
            activity.packageName
        )
        devicePolicyManager.setLockTaskPackages(adminComponent, emptyArray<String>())
        return true
    }

    private fun saveKioskEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(KEY_KIOSK_ACTIVATED, enabled).commit()

    private companion object {
        const val PREFERENCES_NAME = "kiosk_preferences"
        const val KEY_KIOSK_ACTIVATED = "quiosque_ativado"
        var lockTaskStarter: WeakReference<MainActivity>? = null
    }
}
