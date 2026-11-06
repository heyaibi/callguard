package com.callguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.callguard.AppGraph
import com.callguard.CallGuardApp
import com.callguard.call.Dialer
import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.data.CacheEntry
import com.callguard.data.CallLogEntry
import com.callguard.data.Direction
import com.callguard.identity.LookupContext
import com.callguard.identity.ResolutionResult
import com.callguard.identity.SourceKind
import com.callguard.spam.SpamAssessment
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Answers and logs calls. Android binds a third-party screening service only for calls that are not from contacts,
 * so contacts are never screened here.
 *
 * Each call gets exactly one answer. The full screening runs on [worker]. If it has not finished within
 * [SCREENING_DEADLINE_MILLIS], the timer answers with the lists-only fallback and the late result is dropped.
 * Outgoing calls are allowed at once and are only logged.
 */
class CallGuardScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val answer = CallAnswer { decision -> respond(callDetails, decision) }
        try {
            val graph = CallGuardApp.graph(this)
            val settings = graph.settingsStore.settings.value
            val raw = callDetails.handle?.schemeSpecificPart
            val call = CallInfo(
                raw = raw,
                cnap = trustedCnapName(
                    name = callDetails.callerDisplayName,
                    presentation = callDetails.callerDisplayNamePresentation,
                    allowedPresentation = TelecomManager.PRESENTATION_ALLOWED,
                ),
                // Unknown direction is screened like an incoming call: only outgoing calls skip screening.
                direction = if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) Direction.OUTGOING else Direction.INCOMING,
                arrivedAt = System.currentTimeMillis(),
            )
            val number = raw?.let { PhoneNumbers.normalize(it) }
            val listsOnly = answerFor(call.direction, number?.e164, settings, screened = null)

            // Outgoing calls are answered at once. Incoming calls wait for the full screening, bounded by the deadline.
            val deadline = if (call.direction == Direction.INCOMING) {
                timer.schedule(Runnable { answer.offer(listsOnly) }, SCREENING_DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
            } else {
                answer.offer(listsOnly)
                null
            }
            worker.execute {
                runCatching {
                    val screened = number?.let { runCatching { screen(graph, it, call.cnap) }.getOrNull() }
                    val sent = answer.offer(answerFor(call.direction, number?.e164, settings, screened?.decision))
                    deadline?.cancel(false)
                    afterAnswer(graph, call, number, screened, sent)
                }
            }
        } catch (t: Throwable) {
            answer.offer(ScreeningDecision.ALLOW)
        }
    }

    /** The full screening: identity, spam assessment and the decision the rules give. */
    private fun screen(graph: AppGraph, number: PhoneNumber, cnap: String?): Screened {
        val identity = graph.resolutionPolicy.resolve(number, LookupContext(cnap))
        val spam = graph.spamPolicy.assess(number)
        val decision = graph.screeningPolicy.decide(identity, spam, graph.effectiveSettings(), graph.isInContacts(number))
        return Screened(identity, spam, decision)
    }

    /** Sends one answer to the platform. A failure here leaves the platform's own default in place. */
    private fun respond(callDetails: Call.Details, decision: ScreeningDecision) {
        // Recorded before the answer goes out, so the in-call service finds it when the platform adds the call.
        ScreeningVerdicts.record(ScreeningVerdicts.keyFor(callDetails.handle?.schemeSpecificPart), decision)
        val spec = decision.responseSpec()
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(spec.disallowCall)
            .setRejectCall(spec.rejectCall)
            .setSkipCallLog(spec.skipCallLog)
            .setSkipNotification(spec.skipNotification)
            .setSilenceCall(spec.silenceCall)
            .build()
        runCatching { respondToCall(callDetails, response) }
    }

    /**
     * Logging, CNAP learning and the notices. Runs after the answer went out, so nothing here delays the call.
     * The log records the answer that was actually sent, which may be the deadline fallback.
     */
    private fun afterAnswer(graph: AppGraph, call: CallInfo, number: PhoneNumber?, screened: Screened?, sent: ScreeningDecision) {
        val identity = screened?.identity
        val name = identity?.name ?: call.cnap
        val source = identity?.source ?: if (call.cnap != null) SourceKind.CNAP else SourceKind.NONE
        runCatching {
            graph.repos.calls.add(
                CallLogEntry(
                    number = number?.e164 ?: call.raw?.takeIf { it.isNotBlank() } ?: PRIVATE_NUMBER,
                    name = name,
                    source = source,
                    direction = call.direction,
                    outcome = if (call.direction == Direction.OUTGOING) "OUTGOING" else sent.logOutcome(),
                    timestamp = call.arrivedAt,
                ),
            )
        }
        if (number != null && call.cnap != null) {
            runCatching { graph.repos.cache.put(CacheEntry(number.e164, call.cnap, "call", System.currentTimeMillis())) }
        }
        if (call.direction == Direction.INCOMING && number != null) {
            runCatching {
                val notifier = CallerIdNotifier(this)
                when {
                    sent == ScreeningDecision.BLOCK -> notifier.showBlocked(number, name)
                    // As the default dialer the in-call screen already shows who is calling, so no card.
                    Dialer.isDefaultDialer(this) -> Unit
                    screened != null && (screened.identity.isKnown || graph.settingsStore.settings.value.showCardForUnknown) ->
                        notifier.showCard(number, screened.identity, screened.spam, sent)
                }
            }
        }
    }

    private companion object {
        /** Number recorded for a call with no usable number, such as a private or hidden caller. */
        const val PRIVATE_NUMBER = "PRIVATE"

        /** Full screening runs here, so a slow lookup never blocks the main thread. Two threads let two calls resolve at once. */
        val worker = Executors.newFixedThreadPool(2, daemonThreads("callguard-screening"))

        /** Fires the deadline on its own thread, so a busy worker cannot delay the answer. */
        val timer = Executors.newSingleThreadScheduledExecutor(daemonThreads("callguard-deadline"))
    }
}

private fun daemonThreads(name: String) = ThreadFactory { task -> Thread(task, name).apply { isDaemon = true } }

/** What the service reads from one platform call, copied out of Call.Details so the work can leave the callback. */
private class CallInfo(val raw: String?, val cnap: String?, val direction: Direction, val arrivedAt: Long)

/** A completed full screening of one number. */
private class Screened(val identity: ResolutionResult, val spam: SpamAssessment, val decision: ScreeningDecision)
