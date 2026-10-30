package freezekit.capitaldascestas.config

import android.content.Context
import android.content.SharedPreferences
import java.net.IDN
import java.net.URI
import java.util.Locale

class BrowserConfig(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val initialUrl: String
        get() = preferences.getString(KEY_INITIAL_URL, DEFAULT_INITIAL_URL) ?: DEFAULT_INITIAL_URL

    val allowedHosts: Set<String>
        get() = (preferences.getStringSet(KEY_ALLOWED_HOSTS, DEFAULT_ALLOWED_HOSTS.toMutableSet())
            ?: DEFAULT_ALLOWED_HOSTS).toSet()

    val cameraHosts: Set<String>
        get() = (preferences.getStringSet(KEY_CAMERA_HOSTS, DEFAULT_CAMERA_HOSTS.toMutableSet())
            ?: DEFAULT_CAMERA_HOSTS).toSet()

    /** Retorna null em sucesso ou uma explicação curta; nada é persistido se houver erro. */
    fun saveConfiguration(urlInput: String, allowedInput: String, cameraInput: String): String? {
        val url = urlInput.trim()
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return "A URL inicial não é válida."
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) {
            return "A URL inicial deve usar HTTPS e não pode conter usuário/senha."
        }
        val initialHost = normalizeHost(uri.host)
            ?: return "A URL inicial precisa conter um host DNS válido."
        if (uri.port != -1 && uri.port != 443) {
            return "Use HTTPS na porta padrão (443)."
        }

        val allowed = parseHostList(allowedInput)
            ?: return "A lista de endereços liberados contém um host inválido."
        if (allowed.isEmpty()) return "A lista de endereços liberados não pode ficar vazia."
        if (initialHost !in allowed) {
            return "O host da URL inicial precisa estar na lista de endereços liberados."
        }

        val cameras = parseHostList(cameraInput)
            ?: return "A lista de endereços com câmera contém um host inválido."
        if (!allowed.containsAll(cameras)) {
            return "Cada host com câmera também precisa estar na lista de endereços liberados."
        }

        val saved = preferences.edit()
            .putString(KEY_INITIAL_URL, url)
            .putStringSet(KEY_ALLOWED_HOSTS, allowed.toMutableSet())
            .putStringSet(KEY_CAMERA_HOSTS, cameras.toMutableSet())
            .commit()
        return if (saved) null else "Não foi possível salvar a configuração."
    }

    private fun parseHostList(input: String): Set<String>? {
        val entries = input.split(Regex("[,;\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val normalized = entries.map { normalizeHost(it) ?: return null }.toSet()
        return normalized
    }

    private fun normalizeHost(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim().removeSuffix(".")
        if (trimmed.isBlank() || trimmed.contains('*') || trimmed.any { it.isWhitespace() }) return null
        val ascii = try {
            IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (ascii.length > 253 || ascii.contains(':') || ascii.contains('/') || ascii.contains('@')) {
            return null
        }
        val labels = ascii.split('.')
        if (labels.size < 2 || labels.any { label ->
                label.isEmpty() || label.length > 63 ||
                    label.first() == '-' || label.last() == '-' ||
                    label.any { !(it in 'a'..'z' || it in '0'..'9' || it == '-') }
            }
        ) return null
        return ascii
    }

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
