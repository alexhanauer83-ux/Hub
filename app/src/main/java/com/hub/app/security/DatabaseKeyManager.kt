package com.hub.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Erzeugt und verwahrt die Passphrase der SQLCipher-Datenbank.
 *
 * Aufbau der Schlüsselkette:
 *  1. Ein zufälliger 256-Bit-Schlüssel wird einmalig per [SecureRandom] erzeugt.
 *  2. Er wird in [EncryptedSharedPreferences] abgelegt, die ihrerseits mit einem
 *     Master-Key aus dem **Android Keystore** verschlüsselt sind. Der Master-Key
 *     verlässt die Hardware (StrongBox/TEE) nie und ist nicht auslesbar.
 *  3. SQLCipher bekommt die Passphrase nur zur Laufzeit übergeben.
 *
 * Das schützt die Datenbank bei physischem Zugriff auf das Dateisystem (z. B. ADB-Backup,
 * ausgebauter Speicher, Root-Zugriff durch andere Apps).
 *
 * Was es **nicht** schützt: Läuft die App selbst, ist die Passphrase im Prozessspeicher.
 * Gegen einen aktiven Angreifer mit Root auf einem laufenden Gerät hilft keine
 * clientseitige Verschlüsselung – deshalb zusätzlich der [AppLockManager].
 */
class DatabaseKeyManager(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Liefert die Passphrase als ByteArray. SQLCipher **löscht das Array nach dem Öffnen
     * der Datenbank selbst** (zeroize) – es darf daher nicht zwischengespeichert oder
     * wiederverwendet werden. Jeder Aufruf erzeugt deshalb eine frische Kopie.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) return existing.hexToBytes()

        val generated = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PASSPHRASE, generated.toHex()).apply()
        return generated
    }

    /**
     * Nur für "alle Daten löschen": Ohne Passphrase ist die DB-Datei unbrauchbar, ein
     * Löschen des Schlüssels macht die Inhalte damit unwiederbringlich unlesbar.
     */
    fun destroyKey() = prefs.edit().remove(KEY_PASSPHRASE).apply()

    // Hex statt Base64, weil Base64 je nach Padding Sonderzeichen erzeugt und
    // die Roundtrip-Laenge hier fix und leicht pruefbar sein soll.
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val PREFS_NAME = "hub_db_key"
        const val KEY_PASSPHRASE = "passphrase"
        const val KEY_LENGTH_BYTES = 32 // 256 Bit
    }
}
