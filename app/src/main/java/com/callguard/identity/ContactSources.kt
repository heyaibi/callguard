package com.callguard.identity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.callguard.core.PhoneNumber
import com.callguard.prefs.Settings
import kotlinx.coroutines.flow.StateFlow

private const val GOOGLE_ACCOUNT_TYPE = "com.google"

internal data class Contact(val id: Long, val name: String?)

internal fun hasReadContacts(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/** The phonebook entry for [n] across all accounts, or null when absent or unreadable. */
internal fun findContact(context: Context, n: PhoneNumber): Contact? = try {
    context.contentResolver.query(
        Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(n.e164)),
        arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) Contact(c.getLong(0), c.getString(1)) else null }
} catch (e: Exception) {
    null
}

class LocalContactSource(private val context: Context) : LookupSource {
    override val kind = SourceKind.LOCAL_CONTACT
    override fun isAvailable(): Boolean = hasReadContacts(context)
    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? =
        if (isAvailable()) findContact(context, n)?.name else null

    /** True when [n] is in the phonebook. Always false without READ_CONTACTS. */
    fun contains(n: PhoneNumber): Boolean = isAvailable() && findContact(context, n) != null
}

/** Names from Google-account raw contacts. Only used when the user enabled Google Contacts. */
class GoogleContactsSource(private val context: Context, private val settings: StateFlow<Settings>) : LookupSource {
    override val kind = SourceKind.GOOGLE_CONTACTS
    override fun isAvailable(): Boolean = settings.value.googleContacts && hasReadContacts(context)

    override fun lookup(n: PhoneNumber, ctx: LookupContext): String? {
        val contact = findContact(context, n) ?: return null
        return if (hasGoogleRawContact(contact.id)) contact.name else null
    }

    private fun hasGoogleRawContact(contactId: Long): Boolean = try {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(contactId.toString(), GOOGLE_ACCOUNT_TYPE),
            null,
        )?.use { it.count > 0 } == true
    } catch (e: Exception) {
        false
    }
}
