package freezekit.capitaldascestas.config

import android.content.Context
import android.content.SharedPreferences

class BrowserConfig(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val initialUrl: String
        get() = preferences.getString(KEY_INITIAL_URL, DEFAULT_INITIAL_URL)
            ?: DEFAULT_INITIAL_URL

    val allowedHosts: Set<String>
        get() = (preferences.getStringSet(KEY_ALLOWED_HOSTS, DEFAULT_ALLOWED_HOSTS.toMutableSet())
            ?: DEFAULT_ALLOWED_HOSTS).toSet()

    val cameraHosts: Set<String>
        get() = (preferences.getStringSet(KEY_CAMERA_HOSTS, DEFAULT_CAMERA_HOSTS.toMutableSet())
            ?: DEFAULT_CAMERA_HOSTS).toSet()

    companion object {
        private const val PREFERENCES_NAME = "freezekit_browser_config"
        private const val KEY_INITIAL_URL = "initial_url"
        private const val KEY_ALLOWED_HOSTS = "allowed_hosts"
        private const val KEY_CAMERA_HOSTS = "camera_hosts"

        const val DEFAULT_INITIAL_URL = "https://confer.manus.space/"
        private val DEFAULT_ALLOWED_HOSTS =
            setOf("confer.manus.space", "precodebanana.manus.space")
        private val DEFAULT_CAMERA_HOSTS = setOf("confer.manus.space")
    }
}
