package com.callguard.call

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.ui.call.InCallActions
import com.callguard.ui.call.InCallScreen
import com.callguard.ui.theme.CallGuardTheme
import kotlinx.coroutines.delay

/** How long an empty call list waits before the screen closes, for calls that have not reached CallManager yet. */
private const val EMPTY_GRACE_MILLIS = 3_000L

/** How long a call that just ended stays on screen before the screen closes. */
private const val ENDED_LINGER_MILLIS = 1_500L

/**
 * The in-call screen. It shows over the lock screen and turns the display on for a ringing call. The full-screen intent
 * of the call notification and [CallManager] start it. It finishes when no call is left.
 */
class InCallActivity : ComponentActivity() {
    private val actions =
        object : InCallActions {
            override fun answer(id: Long) = CallManager.answer(id)

            override fun decline(id: Long) = CallManager.decline(id)

            override fun hangUp(id: Long) = CallManager.hangUp(id)

            override fun hold(
                id: Long,
                hold: Boolean,
            ) = CallManager.hold(id, hold)

            override fun setMuted(muted: Boolean) = CallManager.setMuted(muted)

            override fun setSpeaker(on: Boolean) = CallManager.setSpeaker(on)

            override fun playDtmf(
                id: Long,
                digit: Char,
            ) = CallManager.playDtmf(id, digit)

            override fun stopDtmf(id: Long) = CallManager.stopDtmf(id)

            /** Opens the dialer over the call, so a second call can be placed. */
            override fun addCall() {
                startActivity(Intent(this@InCallActivity, DialActivity::class.java).setAction(Intent.ACTION_DIAL))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        setContent {
            CallGuardTheme {
                val calls by CallManager.calls.collectAsStateWithLifecycle()
                FinishWhenIdle(calls = calls, onFinished = { finish() })
                InCallScreen(calls = calls, actions = actions)
            }
        }
    }

    companion object {
        /** An intent that brings this screen to the front as its own task. */
        fun intent(context: Context): Intent = Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Brings the screen up. A launch the platform refuses is ignored; the call notification still opens it. */
        fun start(context: Context) {
            runCatching { context.startActivity(intent(context)) }
        }
    }
}

/**
 * Closes the screen once no call is left. A call that just ended stays visible briefly. An empty list right after the
 * screen opens gets a grace period, because the calls may not have arrived yet.
 */
@Composable
private fun FinishWhenIdle(
    calls: List<CallState>,
    onFinished: () -> Unit,
) {
    var sawCall by remember { mutableStateOf(false) }
    LaunchedEffect(calls) {
        if (calls.isNotEmpty()) sawCall = true
        if (calls.any { it.phase != CallPhase.DISCONNECTED }) return@LaunchedEffect
        when {
            calls.isEmpty() && sawCall -> {
                onFinished()
            }

            calls.isEmpty() -> {
                delay(EMPTY_GRACE_MILLIS)
                onFinished()
            }

            else -> {
                delay(ENDED_LINGER_MILLIS)
                onFinished()
            }
        }
    }
}
