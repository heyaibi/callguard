package com.callguard.bridge

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.callguard.AppGraph
import com.callguard.BuildConfig
import com.callguard.CallGuardApp
import com.callguard.core.PhoneNumber
import com.callguard.data.CacheEntry
import com.callguard.data.CallLogEntry
import com.callguard.data.Direction
import com.callguard.identity.APP_ORIGIN_PREFIX
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "AppBridge"
private const val SHELL_PACKAGE = "com.android.shell" // `adb shell cmd notification post`; accepted in debug builds only
private const val DEDUPE_MILLIS = 30_000L
private val APP_LABELS = mapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business")

/**
 * Reads call and message notifications from the apps the user enabled in the App Bridge.
 * It only reads: it never fires a notification action, so it never answers, replies or dials.
 * Parsing and lookups run on one background thread; [OverlayCard] takes care of the main thread.
 */
class AppBridgeListener : NotificationListenerService() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "callguard-bridge") }

    /** elapsedRealtime of the last handled call per notification key. Touched only on [worker]. */
    private val handledAt = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val enabled =
            graph()
                .settingsStore.settings.value.bridgeApps
                .filterValues { it }
                .keys
        val isDebugShell = BuildConfig.DEBUG && pkg == SHELL_PACKAGE
        if (pkg !in enabled && !isDebugShell) return
        val note = readNotification(sbn) ?: return
        val learnNames = pkg in enabled // profile names are learned only for apps the user enabled
        worker.execute { handle(note, learnNames) }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun handle(
        note: Note,
        learnNames: Boolean,
    ) {
        try {
            val graph = graph()
            val number = numberOf(note)
            if (learnNames && number != null) {
                note.profileName()?.let { learnName(graph, number, note.pkg, it, note.postTime) }
            }
            if (number == null || !BridgeParser.isCall(note.category, note.allLines(), note.actionTitles)) return
            if (isRepeat(note.key, SystemClock.elapsedRealtime())) return

            val result = graph.resolve(number.e164) ?: return
            val spam = graph.spamPolicy.assess(number).isSpam
            graph.repos.calls.add(
                CallLogEntry(
                    number = number.e164,
                    name = result.name,
                    source = result.source,
                    direction = Direction.APP,
                    outcome = "ALLOWED",
                    app = note.pkg,
                    timestamp = note.postTime,
                ),
            )
            val name = result.name ?: return // no name, no card: avoids noise
            OverlayCard.show(applicationContext, name, number.e164, subtitleFor(note), spam)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not handle a notification from ${note.pkg}", e)
        }
    }

    private fun graph(): AppGraph = CallGuardApp.graph(applicationContext)

    /** The number in the title. The text is searched only when the title is blank, so a saved name is never second-guessed. */
    private fun numberOf(note: Note): PhoneNumber? = BridgeParser.numberIn(note.title) ?: if (note.title == null) BridgeParser.numberIn(note.text) else null

    private fun Note.profileName(): String? = listOf(title, text, subText, conversationTitle, bigText).firstNotNullOfOrNull(BridgeParser::profileName)

    /** Stores "~ name" for the number. A name learned from a call or SMS is kept; only app-origin entries are replaced. */
    private fun learnName(
        graph: AppGraph,
        number: PhoneNumber,
        pkg: String,
        name: String,
        seenAt: Long,
    ) {
        val known = graph.repos.cache.get(number)
        if (known != null && !known.origin.startsWith(APP_ORIGIN_PREFIX)) return
        graph.repos.cache.put(CacheEntry(number.e164, name, APP_ORIGIN_PREFIX + pkg, seenAt))
    }

    /** True when this key was handled within [DEDUPE_MILLIS]. Entries outside the window are dropped first. */
    private fun isRepeat(
        key: String,
        now: Long,
    ): Boolean {
        handledAt.entries.removeAll { now - it.value > DEDUPE_MILLIS }
        if (handledAt.containsKey(key)) return true
        handledAt[key] = now
        return false
    }

    /** "WhatsApp VIDEO CALL" style caption for the card. */
    private fun subtitleFor(note: Note): String {
        val app = APP_LABELS[note.pkg] ?: note.pkg.substringAfterLast('.')
        val kind = if (BridgeParser.isVideoCall(note.allLines())) "VIDEO CALL" else "CALL"
        return "$app $kind"
    }

    /** Copies the text fields out of the notification. Null when the notification cannot be read. */
    private fun readNotification(sbn: StatusBarNotification): Note? =
        try {
            val n = sbn.notification
            val extras = n.extras

            fun text(key: String): String? = extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
            Note(
                key = sbn.key,
                pkg = sbn.packageName,
                postTime = sbn.postTime,
                category = n.category,
                title = text(Notification.EXTRA_TITLE),
                text = text(Notification.EXTRA_TEXT),
                subText = text(Notification.EXTRA_SUB_TEXT),
                bigText = text(Notification.EXTRA_BIG_TEXT),
                conversationTitle = text(Notification.EXTRA_CONVERSATION_TITLE),
                actionTitles = n.actions.orEmpty().map { it.title?.toString() },
            )
        } catch (e: RuntimeException) {
            null // a malformed notification must not stop the listener
        }
}

/** The text of one notification, copied out so the work can leave the main thread. */
private class Note(
    val key: String,
    val pkg: String,
    val postTime: Long,
    val category: String?,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val conversationTitle: String?,
    val actionTitles: List<String?>,
) {
    fun allLines(): List<String?> = listOf(title, text, bigText, subText, conversationTitle)
}
