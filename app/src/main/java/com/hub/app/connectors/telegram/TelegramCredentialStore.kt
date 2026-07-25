package com.hub.app.connectors.telegram

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert das Bot-Token. Ein Telegram-Bot-Token ist ein Vollzugriff auf den Bot –
 * es gehört damit in denselben Schutzbereich wie ein Passwort und wird deshalb über
 * [EncryptedSharedPreferences] mit einem Android-Keystore-Master-Key abgelegt, nicht
 * in normalen SharedPreferences.
 */
class TelegramCredentialStore(context: Context) {

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

    var botToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    /** Offset des zuletzt verarbeiteten Updates – verhindert doppeltes Einlesen nach Neustart. */
    var lastUpdateId: Long
        get() = prefs.getLong(KEY_OFFSET, 0L)
        set(value) = prefs.edit().putLong(KEY_OFFSET, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val PREFS_NAME = "telegram_credentials"
        const val KEY_TOKEN = "bot_token"
        const val KEY_OFFSET = "last_update_id"
    }
}
