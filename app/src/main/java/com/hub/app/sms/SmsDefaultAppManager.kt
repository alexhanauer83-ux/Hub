package com.hub.app.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Verwaltet die (rein optionale) Rolle als Standard-SMS-App.
 *
 * Warum das überhaupt nötig ist: Über den Notification-Listener sieht Hub von einer SMS
 * nur den Ausschnitt, den die SMS-App in ihre Benachrichtigung schreibt. Erst als
 * Standard-SMS-App darf Hub den `Telephony`-ContentProvider lesen und damit den
 * **vollständigen Verlauf** – inklusive älterer Nachrichten, die nie eine Notification
 * erzeugt haben.
 *
 * Der Preis ist hoch und muss dem Nutzer klar sein: Die Standard-SMS-App ist auch für den
 * *Empfang* zuständig. Solange Hub diese Rolle hat, schreibt kein anderes Programm mehr
 * eingehende SMS in die Datenbank. Deshalb ist die Rolle strikt opt-in und wird nirgends
 * automatisch angefragt.
 */
class SmsDefaultAppManager(private val context: Context) {

    fun isDefaultSmsApp(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /**
     * Ab Android 10 (unser minSdk) läuft die Anfrage über den [RoleManager]; der ältere
     * `ACTION_CHANGE_DEFAULT`-Intent ist dort bereits deprecated. Das Ergebnis kommt als
     * Activity-Result zurück, daher liefert die Methode nur den Intent.
     */
    fun requestRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        @Suppress("DEPRECATION")
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    /**
     * Die Rolle lässt sich nicht programmatisch zurückgeben – der Nutzer muss in den
     * Systemeinstellungen eine andere App wählen. Wir können ihn nur dorthin schicken.
     */
    fun releaseRoleSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
