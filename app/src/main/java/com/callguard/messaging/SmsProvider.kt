package com.callguard.messaging

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.callguard.core.PhoneNumber

/**
 * Reads and writes the system SMS provider. Inserts, updates and deletes need CallGuard to be the default SMS
 * app, and callers check that first. Reads return nothing without READ_SMS.
 */
object SmsProvider {
    private const val CHUNK = 400
    private val CONVERSATION_TYPES = SmsType.CONVERSATION.joinToString(",", prefix = "(", postfix = ")")
    private val COLUMNS =
        arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )

    /** Stores a received text in the inbox. Quiet and blocked texts are stored already read. */
    fun insertInbox(
        context: Context,
        address: String,
        body: String,
        timestamp: Long,
        read: Boolean,
    ) {
        val flag = if (read) 1 else 0
        val values =
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, flag)
                put(Telephony.Sms.SEEN, flag)
            }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    /** Every text in a conversation, newest first. Empty without READ_SMS. */
    fun conversationRows(context: Context): List<SmsRow> = query(context, "${Telephony.Sms.TYPE} IN $CONVERSATION_TYPES")

    /** Texts with [number] in the conversation, oldest first. Empty without READ_SMS. */
    fun thread(
        context: Context,
        number: PhoneNumber,
    ): List<ThreadMessage> =
        rowsWith(context, number)
            .sortedWith(compareBy<SmsRow>({ it.date }, { it.id }))
            .map { ThreadMessage(it.id, it.type, it.body, it.date, it.read) }

    /** Marks the unread texts from [number] as read. Returns how many changed. */
    fun markRead(
        context: Context,
        number: PhoneNumber,
    ): Int {
        val ids = rowsWith(context, number).filter { it.type == SmsType.INBOX && !it.read }.map { it.id }
        val values =
            ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
        return ids.chunked(CHUNK).sumOf { chunk ->
            guarded { context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, idsClause(chunk), null) }
        }
    }

    /** Deletes every text with [number] from the provider. Returns how many rows were deleted. */
    fun deleteConversation(
        context: Context,
        number: PhoneNumber,
    ): Int {
        val ids = rowsWith(context, number).map { it.id }
        return ids.chunked(CHUNK).sumOf { chunk ->
            guarded { context.contentResolver.delete(Telephony.Sms.CONTENT_URI, idsClause(chunk), null) }
        }
    }

    /** Deletes one text from the provider. */
    fun deleteMessage(
        context: Context,
        id: Long,
    ): Int =
        guarded {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms._ID} = ?", arrayOf(id.toString()))
        }

    /**
     * Sends [text] to [number]. As the default app, the text is written to the outbox first and its status then
     * follows the sent callbacks in [SmsStatusReceiver]: pending, sent or failed. Throws when the radio refuses
     * the send, after marking the outbox row failed.
     */
    fun send(
        context: Context,
        number: PhoneNumber,
        text: String,
    ) {
        val manager = smsManager(context)
        val parts = manager.divideMessage(text)
        val id = if (isDefaultSmsApp(context)) insertOutbox(context, number.e164, text, System.currentTimeMillis()) else null
        try {
            if (parts.size == 1) {
                manager.sendTextMessage(number.e164, null, text, id?.let { sentIntent(context, it, 0, 1) }, null)
            } else {
                val sentIntents =
                    id?.let { messageId ->
                        ArrayList(parts.indices.map { sentIntent(context, messageId, it, parts.size) })
                    }
                manager.sendMultipartTextMessage(number.e164, null, parts, sentIntents, null)
            }
        } catch (e: Exception) {
            id?.let { setType(context, it, SmsType.FAILED, onlyWhenPending = false) }
            throw e
        }
    }

    /**
     * Records the result of one sent part. A failed part fails the text. The last part's success sets it sent,
     * unless it already failed.
     */
    fun onPartSent(
        context: Context,
        id: Long,
        success: Boolean,
        lastPart: Boolean,
    ) {
        when {
            !success -> setType(context, id, SmsType.FAILED, onlyWhenPending = false)
            lastPart -> setType(context, id, SmsType.SENT, onlyWhenPending = true)
        }
    }

    private fun rowsWith(
        context: Context,
        number: PhoneNumber,
    ): List<SmsRow> = conversationRows(context).filter { sameAddress(it.address, number) }

    private fun query(
        context: Context,
        selection: String,
    ): List<SmsRow> =
        try {
            context.contentResolver
                .query(Telephony.Sms.CONTENT_URI, COLUMNS, selection, null, "${Telephony.Sms.DATE} DESC")
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val threadCol = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                    val addressCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val typeCol = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                    val readCol = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                    buildList<SmsRow> {
                        while (c.moveToNext()) {
                            add(
                                SmsRow(
                                    id = c.getLong(idCol),
                                    threadId = c.getLong(threadCol),
                                    address = c.getString(addressCol).orEmpty(),
                                    body = c.getString(bodyCol).orEmpty(),
                                    date = c.getLong(dateCol),
                                    type = c.getInt(typeCol),
                                    read = c.getInt(readCol) != 0,
                                ),
                            )
                        }
                    }
                }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }

    private fun insertOutbox(
        context: Context,
        address: String,
        body: String,
        timestamp: Long,
    ): Long? {
        val values =
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
        return try {
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun setType(
        context: Context,
        id: Long,
        type: Int,
        onlyWhenPending: Boolean,
    ) {
        val values = ContentValues().apply { put(Telephony.Sms.TYPE, type) }
        val pending = if (onlyWhenPending) " AND ${Telephony.Sms.TYPE} IN (${SmsType.OUTBOX}, ${SmsType.QUEUED})" else ""
        guarded {
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} = ?$pending", arrayOf(id.toString()))
        }
    }

    private fun sentIntent(
        context: Context,
        id: Long,
        part: Int,
        parts: Int,
    ): PendingIntent {
        val intent =
            Intent(context, SmsStatusReceiver::class.java)
                .setAction(SmsStatusReceiver.ACTION_SENT)
                .setData(Uri.fromParts("sms-sent", "$id/$part", null))
                .putExtra(SmsStatusReceiver.EXTRA_ID, id)
                .putExtra(SmsStatusReceiver.EXTRA_PART, part)
                .putExtra(SmsStatusReceiver.EXTRA_PARTS, parts)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun idsClause(ids: List<Long>) = "${Telephony.Sms._ID} IN (${ids.joinToString(",")})"

    /** Runs a provider write and returns its row count, or 0 when the provider refuses it. */
    private inline fun guarded(block: () -> Int): Int =
        try {
            block()
        } catch (e: SecurityException) {
            0
        }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}
