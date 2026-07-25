package com.hub.app.connectors.imap

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert die IMAP-Zugangsdaten verschlüsselt (Keystore-Master-Key). Ein E-Mail-Passwort
 * ist so sensibel wie jedes andere Passwort und gehört nicht in normale SharedPreferences.
 * Einzel-Konto-Variante (Mehrkonten wäre eine spätere Erweiterung).
 */
class ImapCredentialStore(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): ImapConfig? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return ImapConfig(
            displayName = prefs.getString(KEY_LABEL, user) ?: user,
            host = host,
            port = prefs.getInt(KEY_PORT, 993),
            username = user,
            password = pass,
            useSsl = prefs.getBoolean(KEY_SSL, true)
        )
    }

    fun save(config: ImapConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASS, config.password)
            .putBoolean(KEY_SSL, config.useSsl)
            .putString(KEY_LABEL, config.displayName)
            .apply()
    }

    fun isConfigured(): Boolean = prefs.contains(KEY_HOST)

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val PREFS_NAME = "imap_credentials"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USER = "user"
        const val KEY_PASS = "pass"
        const val KEY_SSL = "ssl"
        const val KEY_LABEL = "label"
    }
}
