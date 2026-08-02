package com.hub.app.connectors.imap

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert IMAP-Zugangsdaten **mehrerer** Konten verschlüsselt (Keystore-Master-Key).
 * Jedes Konto wird über seine [ImapConfig.accountId] adressiert; die Feld-Keys sind mit
 * der accountId präfigiert, ein StringSet hält die Liste der Konten.
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

    fun accountIds(): Set<String> = prefs.getStringSet(KEY_ACCOUNTS, emptySet())?.toSet() ?: emptySet()

    fun isConfigured(): Boolean = accountIds().isNotEmpty()

    fun load(accountId: String): ImapConfig? {
        val host = prefs.getString(k(accountId, HOST), null) ?: return null
        val user = prefs.getString(k(accountId, USER), null) ?: return null
        val pass = prefs.getString(k(accountId, PASS), null) ?: return null
        return ImapConfig(
            accountId = accountId,
            displayName = prefs.getString(k(accountId, LABEL), user) ?: user,
            host = host,
            port = prefs.getInt(k(accountId, PORT), 993),
            username = user,
            password = pass,
            useSsl = prefs.getBoolean(k(accountId, SSL), true)
        )
    }

    fun loadAll(): List<ImapConfig> = accountIds().mapNotNull { load(it) }

    fun save(config: ImapConfig) {
        prefs.edit()
            .putStringSet(KEY_ACCOUNTS, accountIds() + config.accountId)
            .putString(k(config.accountId, HOST), config.host)
            .putInt(k(config.accountId, PORT), config.port)
            .putString(k(config.accountId, USER), config.username)
            .putString(k(config.accountId, PASS), config.password)
            .putBoolean(k(config.accountId, SSL), config.useSsl)
            .putString(k(config.accountId, LABEL), config.displayName)
            .apply()
    }

    fun remove(accountId: String) {
        prefs.edit()
            .putStringSet(KEY_ACCOUNTS, accountIds() - accountId)
            .remove(k(accountId, HOST))
            .remove(k(accountId, PORT))
            .remove(k(accountId, USER))
            .remove(k(accountId, PASS))
            .remove(k(accountId, SSL))
            .remove(k(accountId, LABEL))
            .apply()
    }

    private fun k(accountId: String, field: String) = "$accountId::$field"

    private companion object {
        const val PREFS_NAME = "imap_credentials"
        const val KEY_ACCOUNTS = "accounts"
        const val HOST = "host"
        const val PORT = "port"
        const val USER = "user"
        const val PASS = "pass"
        const val SSL = "ssl"
        const val LABEL = "label"
    }
}
