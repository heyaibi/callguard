package com.callguard.contacts

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.callguard.core.PhoneNumbers

/** One phonebook contact with a phone number. [number] is E.164 when it normalizes, the raw text otherwise. */
data class PhoneContact(
    val id: Long,
    val lookupKey: String?,
    val name: String,
    val number: String,
    val photoUri: String?,
    val starred: Boolean,
)

fun hasWriteContacts(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

/**
 * Every contact that has a phone number, one row per contact, sorted by name.
 * Empty without READ_CONTACTS or when the provider cannot be read. Call it off the main thread.
 */
fun loadPhonebook(context: Context): List<PhoneContact> {
    val resolver = context.contentResolver
    return try {
        val firstNumber = LinkedHashMap<Long, String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (!firstNumber.containsKey(id)) c.getString(1)?.takeIf { it.isNotBlank() }?.let { firstNumber[id] = it }
            }
        }
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
            ),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            null,
            null,
        )?.use { c ->
            val out = ArrayList<PhoneContact>(c.count)
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val raw = firstNumber[id] ?: continue
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: raw
                out += PhoneContact(
                    id = id,
                    lookupKey = c.getString(1),
                    name = name,
                    number = PhoneNumbers.normalize(raw)?.e164 ?: raw,
                    photoUri = c.getString(3),
                    starred = c.getInt(4) == 1,
                )
            }
            out.sortedWith(compareBy({ sectionOf(it.name) == '#' }, { it.name.uppercase() }))
        }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }
}

/** Sets the contact's favourite flag. Needs WRITE_CONTACTS. */
fun setStarred(context: Context, contactId: Long, starred: Boolean): Boolean = try {
    val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
    context.contentResolver.update(contactUri(contactId), values, null, null) > 0
} catch (e: Exception) {
    false
}

/** Deletes the whole contact. Needs WRITE_CONTACTS. */
fun deleteContact(context: Context, contactId: Long): Boolean = try {
    context.contentResolver.delete(contactUri(contactId), null, null) > 0
} catch (e: Exception) {
    false
}

private fun contactUri(contactId: Long): Uri =
    Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())

/** The list section for [name]: its first letter in caps, or '#' when it does not start with a letter. */
fun sectionOf(name: String): Char {
    val first = name.trimStart().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}
