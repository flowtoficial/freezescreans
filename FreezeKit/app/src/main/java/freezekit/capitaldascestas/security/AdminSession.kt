package freezekit.capitaldascestas.security

import android.content.Context
import android.os.SystemClock
import freezekit.capitaldascestas.log.EventLog
import freezekit.capitaldascestas.log.EventType

/** Estado de sessão apenas em memória: um reinício do processo sempre exige novo código. */
object AdminSession {
    const val SESSION_LENGTH_MS = 10 * 60 * 1_000L
    @Volatile private var expiresAtElapsedRealtime = 0L
    @Volatile private var authenticatedWithRecovery = false

    @Synchronized
    fun start(recoveryAuthenticated: Boolean = false) {
        expiresAtElapsedRealtime = SystemClock.elapsedRealtime() + SESSION_LENGTH_MS
        authenticatedWithRecovery = recoveryAuthenticated
    }

    @Synchronized
    fun registerTouch(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (expiresAtElapsedRealtime <= now) {
            return false
        }
        expiresAtElapsedRealtime = now + SESSION_LENGTH_MS
        return true
    }

    @Synchronized
    fun isActive(): Boolean = remainingMillis() > 0L

    @Synchronized
    fun isAuthenticatedWithRecovery(): Boolean = isActive() && authenticatedWithRecovery

    @Synchronized
    fun remainingMillis(): Long {
        val deadline = expiresAtElapsedRealtime
        if (deadline <= 0L) return 0L
        return (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    @Synchronized
    fun end(context: Context, reason: String) {
        val hadSession = expiresAtElapsedRealtime > 0L
        expiresAtElapsedRealtime = 0L
        authenticatedWithRecovery = false
        if (hadSession) {
            val eventReason = when (reason) {
                "expired" -> "expirou"
                "screen_off" -> "tela_apagada"
                "back" -> "voltar"
                else -> "saida_manual"
            }
            EventLog.record(context, EventType.ADMIN_SESSION_ENDED, "reason=$eventReason")
        }
    }
}
