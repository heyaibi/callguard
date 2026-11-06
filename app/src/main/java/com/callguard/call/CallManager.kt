package com.callguard.call

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import com.callguard.CallGuardApp
import com.callguard.data.CallLogEntry
import com.callguard.data.Direction
import com.callguard.identity.Confidence
import com.callguard.identity.SourceKind
import com.callguard.screening.ScreeningDecision
import com.callguard.screening.ScreeningVerdicts
import com.callguard.screening.trustedCnapName
import com.callguard.ui.displayNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * The calls CallGuard is handling. [CallGuardInCallService] feeds it, and the in-call screens, the notifications and
 * the ringer read it. Changes happen on the main thread. Name resolution runs on a background thread and reports
 * back on the main thread.
 */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nextId = AtomicLong(1L)
    private val tracked = mutableListOf<TrackedCall>()
    private val ringerSilenced = mutableSetOf<Long>()
    private var service: InCallService? = null
    private var appContext: Context? = null
    private var audio: CallAudioState? = null

    /** CallGuard screens that are started (the main app and the in-call screen). Counted, so overlap ends with the last. */
    private var startedScreens = 0

    /** Whether a CallGuard screen is in front. A ringing call pops up as a heads-up only while none is. */
    private val uiVisible: Boolean get() = startedScreens > 0

    private val _calls = MutableStateFlow<List<CallState>>(emptyList())

    /** Every call CallGuard tracks, oldest first. A disconnected call stays until Telecom removes it. */
    val calls: StateFlow<List<CallState>> = _calls.asStateFlow()

    /** Called when [CallGuardInCallService] is created. */
    fun attach(service: InCallService) {
        this.service = service
        appContext = service.applicationContext
        audio = service.callAudioState
    }

    /**
     * Called when [CallGuardInCallService] is destroyed. Telecom re-adds any live call when the service binds again,
     * so the tracked calls are dropped here.
     */
    fun detach(service: InCallService) {
        if (this.service !== service) return
        val context = appContext
        tracked.forEach { entry ->
            runCatching { entry.call.unregisterCallback(entry.callback) }
            context?.let { CallNotifier.cancel(it, entry.id) }
        }
        tracked.clear()
        ringerSilenced.clear()
        CallRinger.stop()
        this.service = null
        audio = null
        publish()
    }

    /** Telecom added [call]. An incoming call is screened only when CallGuard recorded a verdict for its number. */
    fun add(
        call: Call,
        now: Long = System.currentTimeMillis(),
    ) {
        if (tracked.any { it.call === call }) return
        val details = call.details
        val raw = details.handle?.schemeSpecificPart
        val key = ScreeningVerdicts.keyFor(raw)
        val verdict = ScreeningVerdicts.peek(key, now)
        val direction = if (details.callDirection == Call.Details.DIRECTION_OUTGOING) Direction.OUTGOING else Direction.INCOMING
        val id = nextId.getAndIncrement()
        val callback =
            object : Call.Callback() {
                override fun onStateChanged(
                    changed: Call,
                    state: Int,
                ) = stateChanged(id)
            }
        val entry =
            TrackedCall(
                id = id,
                call = call,
                callback = callback,
                direction = direction,
                raw = raw,
                key = key,
                cnap =
                    trustedCnapName(
                        name = details.callerDisplayName,
                        presentation = details.callerDisplayNamePresentation,
                        allowedPresentation = TelecomManager.PRESENTATION_ALLOWED,
                    ),
                arrivedAt = now,
                silencedByScreening =
                    verdict == ScreeningDecision.SILENCE ||
                        details.extras?.getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED) == true,
            )
        entry.phase = phaseFor(call.state)
        if (entry.phase == CallPhase.ACTIVE) entry.connectTime = connectTimeOf(details, now)
        tracked += entry
        call.registerCallback(callback)
        resolve(entry)
        val context = appContext
        // A placed call, or a ringing call while CallGuard is in front, brings the in-call screen up. Over another app
        // a ringing call only raises its heads-up, and tapping that opens the screen.
        if (context != null && startsInCallUi(direction, entry.phase, entry.silencedByScreening, uiVisible, isScreenOn(context))) {
            InCallActivity.start(context)
        }
        publish()
    }

    /** A CallGuard screen started. Ringing calls re-post their notifications, since this can change what they look like. */
    fun screenStarted() = screenCountChanged(startedScreens + 1)

    /** A CallGuard screen stopped. */
    fun screenStopped() = screenCountChanged((startedScreens - 1).coerceAtLeast(0))

    private fun screenCountChanged(count: Int) {
        val wasVisible = uiVisible
        startedScreens = count
        if (uiVisible != wasVisible) refreshRinging()
    }

    /** Re-posts the ringing calls on the channel that fits now. The same notification id replaces the old one. */
    private fun refreshRinging() {
        val context = appContext ?: return
        val screenOn = isScreenOn(context)
        _calls.value.filter { it.phase == CallPhase.RINGING }.forEach { CallNotifier.show(context, it, uiVisible, screenOn) }
    }

    /** Telecom removed [call]. Logs it when CallGuard did not screen it, and clears its notification. */
    fun remove(call: Call) {
        val entry = tracked.firstOrNull { it.call === call } ?: return
        tracked.remove(entry)
        runCatching { call.unregisterCallback(entry.callback) }
        ringerSilenced.remove(entry.id)
        appContext?.let { CallNotifier.cancel(it, entry.id) }
        // Screening logs every call it answers, and it records a verdict for each. A call with a verdict is already logged.
        if (ScreeningVerdicts.peek(entry.key) != null) ScreeningVerdicts.forget(entry.key) else logUnscreened(entry)
        publish()
    }

    /** The platform reports the audio route or the mute state. */
    fun onAudioChanged(state: CallAudioState) {
        audio = state
        publish()
    }

    /** The user pressed a hardware key to silence the ringer. Ringing calls stay on screen without sound. */
    fun silenceRinger() {
        tracked.filter { it.phase == CallPhase.RINGING }.forEach { ringerSilenced += it.id }
        CallRinger.stop()
    }

    fun answer(id: Long) {
        find(id)?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Rejects a ringing call without a message. */
    fun decline(id: Long) {
        find(id)?.call?.reject(false, null)
    }

    fun hangUp(id: Long) {
        find(id)?.call?.disconnect()
    }

    fun hold(
        id: Long,
        hold: Boolean,
    ) {
        val call = find(id)?.call ?: return
        if (hold) call.hold() else call.unhold()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    /** Speaker on routes audio to the speaker when the device offers it. Speaker off returns the route to the earpiece. */
    fun setSpeaker(on: Boolean) {
        val inCall = service ?: return
        val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
        if (on) {
            val supported = audio?.supportedRouteMask ?: CallAudioState.ROUTE_SPEAKER
            if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) inCall.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        } else if (route == CallAudioState.ROUTE_SPEAKER) {
            inCall.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
        }
    }

    fun playDtmf(
        id: Long,
        digit: Char,
    ) {
        find(id)?.call?.playDtmfTone(digit)
    }

    fun stopDtmf(id: Long) {
        find(id)?.call?.stopDtmfTone()
    }

    private fun stateChanged(id: Long) {
        val entry = find(id) ?: return
        entry.phase = phaseFor(entry.call.state)
        if ((entry.phase == CallPhase.ACTIVE || entry.phase == CallPhase.HOLDING) && entry.connectTime == null) {
            entry.connectTime = connectTimeOf(entry.call.details, System.currentTimeMillis())
        }
        publish()
    }

    private fun find(id: Long): TrackedCall? = tracked.firstOrNull { it.id == id }

    /** Publishes a snapshot of every tracked call, then updates the notifications and the ringer. */
    private fun publish() {
        val states = tracked.map { it.snapshot(audio) }
        _calls.value = states
        appContext?.let { context ->
            val screenOn = isScreenOn(context)
            states.forEach { CallNotifier.show(context, it, uiVisible, screenOn) }
            syncInProgress(context)
        }
        syncRinger()
    }

    /** Whether the screen is on. With it off, a ringing call relies on the full-screen intent. */
    private fun isScreenOn(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** The call in progress that the foreground notification shows: an active call first, else a held or dialing one. */
    fun inProgressCall(): CallState? {
        val states = _calls.value
        return states.firstOrNull { it.phase == CallPhase.ACTIVE }
            ?: states.firstOrNull { it.phase == CallPhase.HOLDING || it.phase == CallPhase.DIALING }
    }

    /**
     * Keeps the call in progress in the foreground service. When the platform refuses the service start, the call is
     * posted as a plain notification instead. With no call in progress, the service and its notification stop.
     */
    private fun syncInProgress(context: Context) {
        val call = inProgressCall()
        if (call == null) {
            CallForegroundService.stop(context)
            CallNotifier.cancelOngoing(context)
            return
        }
        runCatching { CallForegroundService.start(context) }.onFailure {
            CallNotifier.postPlainOngoing(context, call)
        }
    }

    /** Rings for the oldest ringing call that screening did not silence. Anything else stops the ringer. */
    private fun syncRinger() {
        val context = appContext ?: return
        val audible =
            tracked.firstOrNull {
                it.phase == CallPhase.RINGING && !it.silencedByScreening && it.id !in ringerSilenced
            }
        if (audible == null) {
            CallRinger.stop()
            return
        }
        val dnd =
            dndSuppresses(
                platformSaysSuppressed =
                    audible.call.details.extras
                        ?.getBoolean(Call.EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB) == true,
                sdkInt = Build.VERSION.SDK_INT,
                interruptionFilter =
                    context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
                        ?: NotificationManager.INTERRUPTION_FILTER_ALL,
            )
        val ringerMode = context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val plan = ringPlan(silenced = false, dndSuppressed = dnd, ringerMode = ringerMode)
        if (plan.any) CallRinger.start(context, plan) else CallRinger.stop()
    }

    /**
     * Writes the log entry for a call CallGuard did not screen (a contact, or a call placed from the device). Screened
     * calls are logged by the screening service, so they are skipped here.
     */
    private fun logUnscreened(entry: TrackedCall) {
        val context = appContext ?: return
        val outcome = unscreenedOutcome(entry.direction, answered = entry.connectTime != null)
        val known = entry.identity
        scope.launch(Dispatchers.IO) {
            runCatching {
                val identity = known ?: resolveIdentity(context, entry.raw, entry.cnap)
                CallGuardApp.graph(context).repos.calls.add(
                    CallLogEntry(
                        number = entry.key,
                        name = identity.name,
                        source = identity.source,
                        direction = entry.direction,
                        outcome = outcome,
                        timestamp = entry.arrivedAt,
                    ),
                )
            }
        }
    }

    /** Looks up the name and spam score of the call's number off the main thread, then publishes the result. */
    private fun resolve(entry: TrackedCall) {
        val context = appContext ?: return
        scope.launch {
            val identity =
                withContext(Dispatchers.IO) {
                    runCatching { resolveIdentity(context, entry.raw, entry.cnap) }.getOrNull()
                }
            if (tracked.any { it === entry }) {
                entry.identity = identity
                publish()
            }
        }
    }
}

/** A call CallManager tracks. The mutable fields change on the main thread only. */
private class TrackedCall(
    val id: Long,
    val call: Call,
    val callback: Call.Callback,
    val direction: Direction,
    val raw: String?,
    val key: String,
    val cnap: String?,
    val arrivedAt: Long,
    val silencedByScreening: Boolean,
) {
    var phase: CallPhase = CallPhase.DIALING
    var connectTime: Long? = null
    var identity: Identity? = null

    fun snapshot(audio: CallAudioState?): CallState {
        val known = identity
        val hidden = key == ScreeningVerdicts.PRIVATE_KEY
        return CallState(
            id = id,
            direction = direction,
            number = key,
            displayNumber = if (hidden) "PRIVATE NUMBER" else displayNumber(key),
            hidden = hidden,
            name = known?.name ?: cnap,
            source = known?.source ?: if (cnap != null) SourceKind.CNAP else SourceKind.NONE,
            confidence = known?.confidence ?: if (cnap != null) Confidence.HIGH else Confidence.NONE,
            spam = known?.spam == true,
            spamScore = known?.spamScore ?: 0,
            phase = phase,
            connectTime = connectTime,
            silenced = silencedByScreening,
            muted = audio?.isMuted == true,
            route = audioRouteFor(audio?.route ?: CallAudioState.ROUTE_EARPIECE),
            canHold = call.details.can(Call.Details.CAPABILITY_HOLD),
        )
    }
}

/** What is known about a call's number. Reads contacts and the database, so it runs off the main thread. */
private class Identity(
    val name: String?,
    val source: SourceKind,
    val confidence: Confidence,
    val spam: Boolean,
    val spamScore: Int,
)

private fun resolveIdentity(
    context: Context,
    raw: String?,
    cnap: String?,
): Identity {
    val graph = CallGuardApp.graph(context)
    val result =
        raw?.takeIf { it.isNotBlank() }?.let { graph.resolve(it, cnap) }
            ?: return if (cnap != null) {
                Identity(cnap, SourceKind.CNAP, Confidence.HIGH, spam = false, spamScore = 0)
            } else {
                Identity(null, SourceKind.NONE, Confidence.NONE, spam = false, spamScore = 0)
            }
    val spam = graph.spamPolicy.assess(result.number)
    return Identity(result.name, result.source, result.confidence, spam.isSpam, spam.score)
}

/** The connect time from the platform, or [fallback] when the platform has not set one. */
private fun connectTimeOf(
    details: Call.Details,
    fallback: Long,
): Long = details.connectTimeMillis.takeIf { it > 0L } ?: fallback
