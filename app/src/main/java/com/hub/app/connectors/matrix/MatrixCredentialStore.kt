package com.hub.app.connectors.matrix

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert die Matrix-Session verschlüsselt (Keystore-gesicherter Master-Key).
 * Ein Access-Token ist Vollzugriff auf das Konto und gehört damit in denselben
 * Schutzbereich wie ein Passwort – nie in normale SharedPreferences.
 */
class MatrixCredentialStore(context: Context) {

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

    /** Basis-URL des Homeservers, inkl. Schema, ohne abschließenden Slash. */
    var homeserver: String?
        get() = prefs.getString(KEY_HOMESERVER, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_HOMESERVER, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_USER_ID, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_DEVICE_ID, value).apply()

    /** `next_batch`-Token des letzten /sync – verhindert doppeltes Einlesen nach Neustart. */
    var syncSince: String?
        get() = prefs.getString(KEY_SINCE, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_SINCE, value).apply()

    fun isConfigured(): Boolean = !accessToken.isNullOrBlank() && !homeserver.isNullOrBlank()

    fun clear() = prefs.edit().clear().apply()

    private fun android.content.SharedPreferences.Editor.putStringOrRemove(
        key: String,
        value: String?
    ) = apply { if (value == null) remove(key) else putString(key, value) }

    private companion object {
        const val PREFS_NAME = "matrix_credentials"
        const val KEY_HOMESERVER = "homeserver"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SINCE = "sync_since"
    }
}
