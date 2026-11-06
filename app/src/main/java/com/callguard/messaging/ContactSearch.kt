package com.callguard.messaging

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.callguard.core.PhoneNumber
import com.callguard.identity.hasReadContacts

/** A phonebook match for the To field: a name with one of its numbers. */
data class ContactSuggestion(val name: String, val number: PhoneNumber)

/**
 * Phonebook numbers whose name or number matches [query], one per number. Empty without READ_CONTACTS or for a blank
 * query.
 */
fun searchContacts(context: Context, query: String, limit: Int = 25): List<ContactSuggestion> {
    val q = query.trim()
    if (q.isEmpty() || !hasReadContacts(context)) return emptyList()
    return try {
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q)),
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { c ->
            buildList<ContactSuggestion> {
                while (c.moveToNext() && size < limit) {
                    val raw = c.getString(1).orEmpty().trim()
                    if (raw.isEmpty()) continue
                    add(ContactSuggestion(name = c.getString(0).orEmpty().ifBlank { raw }, number = addressToNumber(raw)))
                }
            }.distinctBy { it.number }
        }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }
}
