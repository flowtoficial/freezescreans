package freezekit.capitaldascestas.log

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Registro local privado do app. A API recebe tipos fixos e detalhes curtos; URLs e códigos
 * reconhecíveis são removidos antes da gravação. Toda operação de arquivo é sincronizada.
 */
enum class EventType(val code: String) {
    APP_STARTED("APP_STARTED"),
    DEVICE_BOOTED("DEVICE_BOOTED"),
    AUTH_TOTP_CORRECT("AUTH_TOTP_CORRECT"),
    AUTH_TOTP_INCORRECT("AUTH_TOTP_INCORRECT"),
    AUTH_RECOVERY_CORRECT("AUTH_RECOVERY_CORRECT"),
    AUTH_RECOVERY_INCORRECT("AUTH_RECOVERY_INCORRECT"),
    AUTH_LOCKOUT_STARTED("AUTH_LOCKOUT_STARTED"),
    ADMIN_SESSION_STARTED("ADMIN_SESSION_STARTED"),
    ADMIN_SESSION_ENDED("ADMIN_SESSION_ENDED"),
    KIOSK_ENTERED("KIOSK_ENTERED"),
    KIOSK_EXITED("KIOSK_EXITED"),
    WIZARD_COMPLETED("WIZARD_COMPLETED"),
    TOTP_RECONFIGURED("TOTP_RECONFIGURED"),
    RECOVERY_RECONFIGURED("RECOVERY_RECONFIGURED"),
    CONFIG_INITIAL_ADDRESS_CHANGED("CONFIG_INITIAL_ADDRESS_CHANGED"),
    CONFIG_ALLOWED_HOSTS_CHANGED("CONFIG_ALLOWED_HOSTS_CHANGED"),
    CONFIG_CAMERA_HOSTS_CHANGED("CONFIG_CAMERA_HOSTS_CHANGED"),
    BROWSER_SESSION_CLEARED("BROWSER_SESSION_CLEARED"),
    WIFI_SETTINGS_OPENED("WIFI_SETTINGS_OPENED"),
    PASSWORD_SETTINGS_OPENED("PASSWORD_SETTINGS_OPENED"),
    BROWSER_ADDRESS_BLOCKED("BROWSER_ADDRESS_BLOCKED"),
    BROWSER_RENDERER_RECOVERED("BROWSER_RENDERER_RECOVERED"),
    NETWORK_CONNECTION_LOST("NETWORK_CONNECTION_LOST"),
    NETWORK_CONNECTION_RESTORED("NETWORK_CONNECTION_RESTORED")
}

object EventLog {
    const val FILE_NAME = "events.log"
    const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    const val RETENTION_DAYS = 90
    const val MAINTENANCE_INTERVAL_MS = 24L * 60L * 60L * 1_000L

    private const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"
    private const val DETAIL_LIMIT = 320
    private const val NO_MAINTENANCE = -1L
    private val fileLock = Any()
    private var lastMaintenanceElapsedRealtime = NO_MAINTENANCE

    private val urlPattern = Regex("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\\t|,;]+")
    private val recoveryPattern = Regex("(?i)(?<![A-Z0-9])[A-HJ-NP-Z2-9]{4}(?:[ -]?[A-HJ-NP-Z2-9]{4}){3}(?![A-Z0-9])")
    private val totpSecretPattern = Regex("(?i)(?<![A-Z2-7])[A-Z2-7]{32}(?![A-Z2-7])")
    private val sixDigitCodePattern = Regex("(?<!\\d)\\d{6}(?!\\d)")

    /** Executa a limpeza obrigatória na abertura normal do app; uma falha não afeta a UI. */
    fun initialize(context: Context) {
        synchronized(fileLock) {
            try {
                if (pruneExpiredAndOversized(context.applicationContext)) {
                    lastMaintenanceElapsedRealtime = SystemClock.elapsedRealtime()
                }
            } catch (_: Exception) {
                // O registro é best-effort: falha de arquivo nunca deve fechar o app.
            }
        }
    }

    /** Pode ser chamado por timer diário; internamente não repete a limpeza antes de 24 horas. */
    fun maintainIfDue(context: Context) {
        synchronized(fileLock) {
            try {
                maintainIfDueLocked(context.applicationContext)
            } catch (_: Exception) {
                // O registro é best-effort.
            }
        }
    }

    /** Acrescenta uma linha e aplica o limite de 2 MB, sem propagar falhas de I/O. */
    fun record(context: Context, type: EventType, detail: String = "") {
        synchronized(fileLock) {
            try {
                val appContext = context.applicationContext
                maintainIfDueLocked(appContext)
                val file = File(appContext.filesDir, FILE_NAME)
                if (!file.exists()) file.createNewFile()

                val timestamp = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.ROOT).format(Date())
                val safeDetail = sanitizeDetail(detail)
                val row = buildString {
                    append(timestamp)
                    append('\t')
                    append(type.code)
                    if (safeDetail.isNotEmpty()) {
                        append('\t')
                        append(safeDetail)
                    }
                }
                FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.append(row)
                    writer.newLine()
                }
                enforceSizeLimit(file)
            } catch (_: Exception) {
                // Event logging must never interrupt the task being performed by the app.
            }
        }
    }

    /** Retorna as linhas mais recentes primeiro, para a tela interna de consulta. */
    fun readNewestFirst(context: Context): List<String> = synchronized(fileLock) {
        try {
            val appContext = context.applicationContext
            maintainIfDueLocked(appContext)
            val file = File(appContext.filesDir, FILE_NAME)
            if (!file.exists()) emptyList() else file.readLines(Charsets.UTF_8).asReversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Extrai somente o host de uma URL; nunca devolve caminho, consulta ou fragmento. */
    fun hostOnly(rawAddress: String): String = try {
        Uri.parse(rawAddress).host?.lowercase(Locale.ROOT) ?: "host_indisponivel"
    } catch (_: Exception) {
        "host_indisponivel"
    }

    private fun maintainIfDueLocked(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastMaintenanceElapsedRealtime == NO_MAINTENANCE ||
            now - lastMaintenanceElapsedRealtime >= MAINTENANCE_INTERVAL_MS
        ) {
            if (pruneExpiredAndOversized(context)) {
                lastMaintenanceElapsedRealtime = now
            }
        }
    }

    private fun pruneExpiredAndOversized(context: Context): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return true

        val cutoffCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS)
        }
        val cutoffMillis = cutoffCalendar.timeInMillis
        val oldLines = file.readLines(Charsets.UTF_8)
        val retained = oldLines.filter { line ->
            val timestampMillis = parseTimestamp(line)
            timestampMillis != null && timestampMillis >= cutoffMillis
        }
        if (retained.size != oldLines.size && !replaceContents(file, retained)) return false
        return enforceSizeLimit(file)
    }

    private fun parseTimestamp(line: String): Long? {
        val timestamp = line.substringBefore('\t')
        if (timestamp.length != TIMESTAMP_PATTERN.length) return null
        val parser = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.ROOT).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = parser.parse(timestamp, position) ?: return null
        return if (position.index == timestamp.length) parsed.time else null
    }

    private fun enforceSizeLimit(file: File): Boolean {
        if (!file.exists() || file.length() <= MAX_FILE_BYTES) return true
        val lines = file.readLines(Charsets.UTF_8).toMutableList()
        var totalBytes = lines.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1L }
        var removeCount = 0
        while (totalBytes > MAX_FILE_BYTES && removeCount < lines.size) {
            totalBytes -= lines[removeCount].toByteArray(Charsets.UTF_8).size.toLong() + 1L
            removeCount += 1
        }
        if (removeCount > 0) {
            return replaceContents(file, lines.drop(removeCount))
        }
        return true
    }

    private fun replaceContents(file: File, lines: List<String>): Boolean {
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        return try {
            temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                lines.forEach { line ->
                    writer.append(line)
                    writer.newLine()
                }
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    private fun sanitizeDetail(input: String): String {
        var safe = urlPattern.replace(input) { match -> hostOnly(match.value) }
        safe = recoveryPattern.replace(safe, "[recovery]")
        safe = totpSecretPattern.replace(safe, "[totp-secret]")
        safe = sixDigitCodePattern.replace(safe, "[totp]")
        safe = safe.replace(Regex("[\\r\\n\\t|]+"), " ").trim()
        return safe.take(DETAIL_LIMIT)
    }
}
