package com.hub.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Verwaltet die App-Lock-Einstellung und den Entsperrzustand der laufenden Sitzung.
 *
 * Warum ein App-Lock hier nicht optional-nebensächlich ist: Der Hub bündelt die
 * Nachrichteninhalte *aller* Apps an einem Ort. Wer das entsperrte Gerät kurz in die Hand
 * bekommt, sieht in einer einzigen Liste, was sonst über ein Dutzend einzeln geschützter
 * Apps verteilt wäre. Der Lock stellt den Schutz wieder her, den die Aggregation aufhebt.
 *
 * Der Entsperrzustand lebt bewusst nur im Speicher: Nach Prozessende ist die App wieder
 * gesperrt. Es wird kein "entsperrt bis"-Zeitstempel persistiert, der sich durch
 * Zurückstellen der Uhr aushebeln liesse.
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val biometricManager = BiometricManager.from(context)

    /** Nur im Speicher – bewusst nicht persistiert. */
    @Volatile
    var isUnlockedForSession: Boolean = false
        private set

    var isLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            // Beim Aktivieren die laufende Sitzung nicht sofort sperren - der Nutzer
            // steht ja gerade in den Einstellungen und hat sich implizit ausgewiesen.
            if (value) isUnlockedForSession = true
        }

    fun onUnlockSucceeded() { isUnlockedForSession = true }

    /** Beim Wechsel in den Hintergrund aufrufen, damit die App erneut abfragt. */
    fun lock() { isUnlockedForSession = false }

    fun requiresUnlock(): Boolean = isLockEnabled && !isUnlockedForSession

    /**
     * Prüft, ob überhaupt eine Authentifizierung verfügbar ist. [DEVICE_CREDENTIAL] ist
     * mit eingeschlossen, damit der Lock auch ohne registrierten Fingerabdruck über die
     * Geräte-PIN funktioniert – sonst wäre die Option auf manchen Geräten tot.
     */
    fun canAuthenticate(): Boolean =
        biometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private companion object {
        const val PREFS_NAME = "hub_app_lock"
        const val KEY_ENABLED = "lock_enabled"
    }
}
