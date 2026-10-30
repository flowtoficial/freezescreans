package freezekit.capitaldascestas.security

import android.os.SystemClock

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
    fun registerTouch() {
        val now = SystemClock.elapsedRealtime()
        if (expiresAtElapsedRealtime <= now) {
            clear()
            return
        }
        expiresAtElapsedRealtime = now + SESSION_LENGTH_MS
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
    fun clear() {
        expiresAtElapsedRealtime = 0L
        authenticatedWithRecovery = false
    }
}
