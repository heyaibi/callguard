package com.callguard.data

import com.callguard.identity.SourceKind
import com.callguard.messaging.MessageOutcome
import com.callguard.spam.SpamSource

enum class Direction { INCOMING, OUTGOING, APP }

data class CallLogEntry(
    val id: Long = 0,
    val number: String,
    val name: String?,
    val source: SourceKind,
    val direction: Direction,
    val outcome: String, // ALLOWED | SILENCED | BLOCKED | ...
    val app: String? = null,
    val timestamp: Long,
)

data class MessageLogEntry(
    val id: Long = 0,
    val number: String,
    val name: String?,
    val body: String,
    val outcome: MessageOutcome,
    val timestamp: Long,
)

data class CacheEntry(val number: String, val name: String, val origin: String, val lastSeen: Long) // origin: call | sms | app:<pkg>

data class ReputationEntry(val number: String, val score: Int, val isSpam: Boolean, val source: SpamSource, val updated: Long)
