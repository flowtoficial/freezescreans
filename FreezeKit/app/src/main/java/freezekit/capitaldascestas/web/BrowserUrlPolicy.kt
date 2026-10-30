package freezekit.capitaldascestas.web

import android.net.Uri
import freezekit.capitaldascestas.config.BrowserConfig
import java.util.Locale

class BrowserUrlPolicy(private val config: BrowserConfig) {
    fun isAllowed(url: String): Boolean = isAllowed(Uri.parse(url))

    fun isAllowed(uri: Uri): Boolean =
        hasHttpsSchemeAndExactHost(uri, config.allowedHosts)

    fun isCameraAllowed(origin: Uri): Boolean =
        hasHttpsSchemeAndExactHost(origin, config.cameraHosts)

    private fun hasHttpsSchemeAndExactHost(uri: Uri, hosts: Set<String>): Boolean {
        if (uri.scheme?.equals("https", ignoreCase = true) != true) {
            return false
        }

        val requestHost = uri.host ?: return false
        val normalizedRequestHost = requestHost.lowercase(Locale.ROOT)
        return hosts.any { allowedHost ->
            normalizedRequestHost == allowedHost.lowercase(Locale.ROOT)
        }
    }
}
