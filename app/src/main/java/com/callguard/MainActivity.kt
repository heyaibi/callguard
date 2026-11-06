package com.callguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.callguard.screening.EXTRA_INCOMING_NUMBER
import com.callguard.ui.AppNav
import com.callguard.ui.OpenRequest
import com.callguard.ui.incomingRoute
import com.callguard.ui.keypadRoute
import com.callguard.ui.theme.CallGuardTheme
import com.callguard.ui.threadRoute

/**
 * The one activity. It owns every launch that opens a screen: DIAL and tel: VIEW (the keypad, as the dialer role
 * requires), message notifications and sms: links (a conversation) and caller ID cards (an incoming call). Singletask,
 * so a launch while the app is open arrives in [onNewIntent] and is handed to AppNav the same way.
 */
class MainActivity : ComponentActivity() {
    /** The screen the latest launch asked for. AppNav opens each request once. */
    private var request by mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark: black system bars with light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        // Rotation keeps the open screen instead of replaying the launch.
        if (savedInstanceState == null) request = requestFrom(intent)
        setContent {
            CallGuardTheme {
                AppNav(request = request)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestFrom(intent)?.let { request = it }
    }

    companion object {
        /** Sent by message notifications: opens the conversation named by [EXTRA_THREAD]. */
        const val ACTION_OPEN_THREAD = "com.callguard.action.OPEN_THREAD"
        private const val EXTRA_THREAD = "com.callguard.extra.THREAD"
        private val SMS_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

        /** Opens the conversation with [e164]. */
        fun threadIntent(
            context: Context,
            e164: String,
        ): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_THREAD)
                .putExtra(EXTRA_THREAD, e164)

        /** The screen a launch asks for, or null for a plain launch from the launcher. */
        fun requestFrom(intent: Intent?): OpenRequest? {
            intent ?: return null
            intent.getStringExtra(EXTRA_INCOMING_NUMBER)?.takeIf { it.isNotBlank() }?.let {
                return OpenRequest(incomingRoute(it))
            }
            threadFrom(intent)?.let { return OpenRequest(threadRoute(it)) }
            val isDial = intent.action == Intent.ACTION_DIAL
            val isTelView = intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "tel"
            if (isDial || isTelView) return OpenRequest(keypadRoute(numberFrom(intent)))
            return null
        }

        /** The number a DIAL or tel: VIEW names, or null when it names none (a bare DIAL opens an empty keypad). */
        private fun numberFrom(intent: Intent): String? =
            intent.data
                ?.takeIf { it.scheme == "tel" }
                ?.schemeSpecificPart
                ?.takeIf { it.isNotBlank() }

        /** The conversation a launch asks for: a notification's extra, or an sms:, smsto:, mms: or mmsto: link. */
        private fun threadFrom(intent: Intent): String? {
            if (intent.action == ACTION_OPEN_THREAD) {
                intent.getStringExtra(EXTRA_THREAD)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            val uri = intent.data ?: return null
            val scheme = uri.scheme ?: return null
            if (scheme !in SMS_SCHEMES) return null
            return Uri.decode(uri.schemeSpecificPart.substringBefore('?')).takeIf { it.isNotBlank() }
        }
    }
}
