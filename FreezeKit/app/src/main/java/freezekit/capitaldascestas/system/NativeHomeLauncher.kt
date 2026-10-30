package freezekit.capitaldascestas.system

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager

object NativeHomeLauncher {
    fun open(activity: Activity): Boolean {
        val query = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addCategory(Intent.CATEGORY_DEFAULT)
        val matches = try {
            @Suppress("DEPRECATION")
            activity.packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: SecurityException) {
            return false
        }

        val target = matches.asSequence()
            .mapNotNull { it.activityInfo }
            .firstOrNull { it.packageName != activity.packageName }
            ?: return false
        val component = ComponentName(target.packageName, target.name)
        val explicitHome = Intent(query)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.startActivity(explicitHome)
            true
        } catch (_: Exception) {
            false
        }
    }
}
