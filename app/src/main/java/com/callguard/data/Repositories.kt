package com.callguard.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.callguard.core.PhoneNumber
import com.callguard.identity.SourceKind
import com.callguard.messaging.MessageOutcome
import com.callguard.spam.ReputationStore
import com.callguard.spam.SpamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val OBSERVE_LIMIT = 200

class Repositories(
    db: CallGuardDb,
) {
    val cache = CacheRepository(db)
    val reputation = ReputationRepository(db)
    val calls = CallLogRepository(db)
    val messages = MessageLogRepository(db)

    /** Clears cache, logs and learned reputation. Settings and the user's own spam marks are kept. */
    fun clearAll() {
        cache.clear()
        calls.clear()
        messages.clear()
        reputation.clear(keepUserMarks = true)
    }

    fun exportJson(): String =
        JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "cache",
                JSONArray(
                    cache.all().map {
                        obj("number" to it.number, "name" to it.name, "origin" to it.origin, "lastSeen" to it.lastSeen)
                    },
                ),
            ).put(
                "reputation",
                JSONArray(
                    reputation.all().map {
                        obj("number" to it.number, "score" to it.score, "isSpam" to it.isSpam, "source" to it.source.name, "updated" to it.updated)
                    },
                ),
            ).put(
                "calls",
                JSONArray(
                    calls.recent(Int.MAX_VALUE).map {
                        obj(
                            "number" to it.number,
                            "name" to it.name,
                            "source" to it.source.name,
                            "direction" to it.direction.name,
                            "outcome" to it.outcome,
                            "app" to it.app,
                            "timestamp" to it.timestamp,
                        )
                    },
                ),
            ).put(
                "messages",
                JSONArray(
                    messages.recent(Int.MAX_VALUE).map {
                        obj("number" to it.number, "name" to it.name, "body" to it.body, "outcome" to it.outcome.name, "timestamp" to it.timestamp)
                    },
                ),
            ).toString(2)
}

class CacheRepository(
    private val db: CallGuardDb,
) {
    fun get(n: PhoneNumber): CacheEntry? =
        db.safely(null) {
            db.readableDatabase.query("cache", null, "number = ?", arrayOf(n.e164), null, null, null, "1").use { c ->
                if (c.moveToFirst()) c.toCacheEntry() else null
            }
        }

    fun put(entry: CacheEntry) {
        db.safely(Unit) { db.writableDatabase.insertWithOnConflict("cache", null, cacheValues(entry), SQLiteDatabase.CONFLICT_REPLACE) }
    }

    fun remove(n: PhoneNumber) {
        db.safely(Unit) { db.writableDatabase.delete("cache", "number = ?", arrayOf(n.e164)) }
    }

    fun all(): List<CacheEntry> =
        db.safely(emptyList()) {
            db.readableDatabase.query("cache", null, null, null, null, null, "last_seen DESC").rows { it.toCacheEntry() }
        }

    fun clear() {
        db.safely(Unit) { db.writableDatabase.delete("cache", null, null) }
    }
}

class ReputationRepository(
    private val db: CallGuardDb,
) : ReputationStore {
    override fun get(n: PhoneNumber): ReputationEntry? =
        db.safely(null) {
            db.readableDatabase.query("reputation", null, "number = ?", arrayOf(n.e164), null, null, null, "1").use { c ->
                if (c.moveToFirst()) c.toReputation() else null
            }
        }

    override fun put(entry: ReputationEntry) {
        db.safely(Unit) { db.writableDatabase.insertWithOnConflict("reputation", null, reputationValues(entry), SQLiteDatabase.CONFLICT_REPLACE) }
    }

    override fun remove(n: PhoneNumber) {
        db.safely(Unit) { db.writableDatabase.delete("reputation", "number = ?", arrayOf(n.e164)) }
    }

    fun all(): List<ReputationEntry> =
        db.safely(emptyList()) {
            db.readableDatabase.query("reputation", null, null, null, null, null, "updated DESC").rows { it.toReputation() }
        }

    /** Deletes every entry, or with [keepUserMarks] everything except the user's own spam marks. */
    fun clear(keepUserMarks: Boolean = false) {
        db.safely(Unit) {
            if (keepUserMarks) {
                db.writableDatabase.delete("reputation", "source <> ?", arrayOf(SpamSource.USER.name))
            } else {
                db.writableDatabase.delete("reputation", null, null)
            }
        }
    }
}

class CallLogRepository(
    private val db: CallGuardDb,
) {
    private val flow = MutableStateFlow(recent(OBSERVE_LIMIT))

    /** Most recent calls. Refreshed after every write. */
    fun observe(): StateFlow<List<CallLogEntry>> = flow.asStateFlow()

    fun add(e: CallLogEntry): Long {
        val id = db.safely(-1L) { db.writableDatabase.insert("call_log", null, callValues(e)) }
        refresh()
        return id
    }

    fun recent(limit: Int): List<CallLogEntry> =
        db.safely(emptyList()) {
            db.readableDatabase
                .query("call_log", null, null, null, null, null, "timestamp DESC, id DESC", limit.toString())
                .rows { it.toCallLog() }
        }

    fun countSince(
        n: PhoneNumber,
        sinceMillis: Long,
    ): Int =
        db.safely(0) {
            db.readableDatabase
                .rawQuery(
                    "SELECT COUNT(*) FROM call_log WHERE number = ? AND timestamp >= ?",
                    arrayOf(n.e164, sinceMillis.toString()),
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        }

    fun clear() {
        db.safely(Unit) { db.writableDatabase.delete("call_log", null, null) }
        refresh()
    }

    fun delete(id: Long) {
        db.safely(Unit) { db.writableDatabase.delete("call_log", "id = ?", arrayOf(id.toString())) }
        refresh()
    }

    private fun refresh() {
        flow.value = recent(OBSERVE_LIMIT)
    }
}

class MessageLogRepository(
    private val db: CallGuardDb,
) {
    private val flow = MutableStateFlow(recent(OBSERVE_LIMIT))

    /** Most recent messages of every outcome. Refreshed after every write. */
    fun observe(): StateFlow<List<MessageLogEntry>> = flow.asStateFlow()

    fun add(e: MessageLogEntry): Long {
        val id = db.safely(-1L) { db.writableDatabase.insert("message_log", null, messageValues(e)) }
        refresh()
        return id
    }

    fun recent(limit: Int): List<MessageLogEntry> =
        db.safely(emptyList()) {
            db.readableDatabase
                .query("message_log", null, null, null, null, null, "timestamp DESC, id DESC", limit.toString())
                .rows { it.toMessage() }
        }

    /** Messages kept out of the main inbox: quiet-inbox and blocked senders. */
    fun quietInbox(): List<MessageLogEntry> =
        db.safely(emptyList()) {
            db.readableDatabase
                .query(
                    "message_log",
                    null,
                    "outcome IN (?, ?)",
                    arrayOf(MessageOutcome.QUIET_INBOX.name, MessageOutcome.BLOCKED.name),
                    null,
                    null,
                    "timestamp DESC, id DESC",
                ).rows { it.toMessage() }
        }

    fun delete(id: Long) {
        db.safely(Unit) { db.writableDatabase.delete("message_log", "id = ?", arrayOf(id.toString())) }
        refresh()
    }

    /** Deletes every logged text from [e164], used when its conversation is deleted. */
    fun deleteFor(e164: String) {
        db.safely(Unit) { db.writableDatabase.delete("message_log", "number = ?", arrayOf(e164)) }
        refresh()
    }

    fun clear() {
        db.safely(Unit) { db.writableDatabase.delete("message_log", null, null) }
        refresh()
    }

    private fun refresh() {
        flow.value = recent(OBSERVE_LIMIT)
    }
}

private fun obj(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().also { o -> pairs.forEach { o.put(it.first, it.second) } }

private fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col))

private fun Cursor.optStr(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }

private fun Cursor.num(col: String): Long = getLong(getColumnIndexOrThrow(col))

private fun <T> Cursor.rows(read: (Cursor) -> T): List<T> = use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

private fun Cursor.toCacheEntry() = CacheEntry(str("number"), str("name"), str("origin"), num("last_seen"))

private fun Cursor.toReputation() =
    ReputationEntry(
        number = str("number"),
        score = num("score").toInt(),
        isSpam = num("is_spam") != 0L,
        source = SpamSource.valueOf(str("source")),
        updated = num("updated"),
    )

private fun Cursor.toCallLog() =
    CallLogEntry(
        id = num("id"),
        number = str("number"),
        name = optStr("name"),
        source = SourceKind.valueOf(str("source")),
        direction = Direction.valueOf(str("direction")),
        outcome = str("outcome"),
        app = optStr("app"),
        timestamp = num("timestamp"),
    )

private fun Cursor.toMessage() =
    MessageLogEntry(
        id = num("id"),
        number = str("number"),
        name = optStr("name"),
        body = str("body"),
        outcome = MessageOutcome.valueOf(str("outcome")),
        timestamp = num("timestamp"),
    )

private fun cacheValues(e: CacheEntry) =
    ContentValues().apply {
        put("number", e.number)
        put("name", e.name)
        put("origin", e.origin)
        put("last_seen", e.lastSeen)
    }

private fun reputationValues(e: ReputationEntry) =
    ContentValues().apply {
        put("number", e.number)
        put("score", e.score)
        put("is_spam", e.isSpam)
        put("source", e.source.name)
        put("updated", e.updated)
    }

private fun callValues(e: CallLogEntry) =
    ContentValues().apply {
        put("number", e.number)
        put("name", e.name)
        put("source", e.source.name)
        put("direction", e.direction.name)
        put("outcome", e.outcome)
        put("app", e.app)
        put("timestamp", e.timestamp)
    }

private fun messageValues(e: MessageLogEntry) =
    ContentValues().apply {
        put("number", e.number)
        put("name", e.name)
        put("body", e.body)
        put("outcome", e.outcome.name)
        put("timestamp", e.timestamp)
    }
