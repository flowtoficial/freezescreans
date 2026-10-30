package freezekit.capitaldascestas.system

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import freezekit.capitaldascestas.kiosk.KioskManager

object SystemSettingsLauncher {
    data class LaunchResult(val launched: Boolean, val message: String? = null)

    fun openWifi(activity: Activity): LaunchResult =
        openResolvedSystemActivity(activity, Intent(Settings.ACTION_WIFI_SETTINGS))

    /** A API pública para a senha é DevicePolicyManager.ACTION_SET_NEW_PASSWORD. */
    fun openAndroidPassword(activity: Activity): LaunchResult =
        openResolvedSystemActivity(activity, Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))

    private fun openResolvedSystemActivity(activity: Activity, implicitIntent: Intent): LaunchResult {
        val matches = try {
            @Suppress("DEPRECATION")
            activity.packageManager.queryIntentActivities(
                implicitIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
        } catch (_: SecurityException) {
            return LaunchResult(false, "O Android não permitiu localizar a tela do sistema.")
        }

        val components = matches.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            if (activityInfo.packageName.isNullOrBlank() || activityInfo.name.isNullOrBlank()) {
                return@mapNotNull null
            }
            ComponentName(activityInfo.packageName, activityInfo.name)
        }.distinct()

        if (components.size != 1) {
            return LaunchResult(
                false,
                if (components.isEmpty()) "A tela do sistema não está disponível."
                else "Há mais de uma tela compatível; não foi possível escolher com segurança."
            )
        }

        val target = components.single()
        if (target.packageName == activity.packageName) {
            return LaunchResult(false, "A resolução não encontrou uma tela externa do sistema.")
        }

        val kiosk = KioskManager(activity)
        if (kiosk.isDeviceOwner() &&
            !kiosk.temporarilyAllowLockTaskPackage(target.packageName)
        ) {
            return LaunchResult(false, "Não foi possível liberar temporariamente a tela no quiosque.")
        }

        return try {
            val explicitIntent = Intent(implicitIntent)
                .setComponent(target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(explicitIntent)
            LaunchResult(true)
        } catch (_: ActivityNotFoundException) {
            kiosk.restoreLockTaskPackagesIfNeeded()
            LaunchResult(false, "A tela do sistema deixou de estar disponível.")
        } catch (_: SecurityException) {
            kiosk.restoreLockTaskPackagesIfNeeded()
            LaunchResult(false, "O Android bloqueou a abertura da tela do sistema.")
        }
    }
}
