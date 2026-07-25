package com.hub.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ein Kontakt mit einer Telefonnummer für die Empfängerauswahl. */
data class PhoneContact(
    val name: String,
    val number: String
)

/**
 * Liest Telefonkontakte über den [ContactsContract]-Provider – nur für die
 * Empfängerauswahl beim SMS-Verfassen. Nichts wird gespeichert oder übertragen; die Liste
 * entsteht bei Bedarf im Speicher.
 */
class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val contacts = mutableListOf<PhoneContact>()
        // Nach Name sortiert; Duplikate (gleiche Nummer, normalisiert) überspringen.
        val seenNumbers = HashSet<String>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex)?.takeIf { it.isNotBlank() } ?: continue
                val normalized = number.filter { it.isDigit() || it == '+' }
                if (seenNumbers.add(normalized)) {
                    contacts += PhoneContact(name.trim(), number.trim())
                }
            }
        }
        contacts
    }
}
