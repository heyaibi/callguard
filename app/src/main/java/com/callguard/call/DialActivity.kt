package com.callguard.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.callguard.ui.call.DialScreen
import com.callguard.ui.theme.CallGuardTheme

/**
 * The dialer that the dialer role requires: it answers DIAL, VIEW tel:, CALL tel: and CALL_BUTTON. A CALL that names a
 * number places the call at once. The other intents open the dialer with the number filled in, and "Add call" during a
 * call opens it empty. Placing a call needs CALL_PHONE, which this activity asks for when it is missing.
 */
class DialActivity : ComponentActivity() {
    private var number by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)
    private var isDefaultDialer by mutableStateOf(false)

    /** The number waiting for the CALL_PHONE answer. */
    private var pendingCall: String? = null

    private val callPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingCall
            pendingCall = null
            if (granted && pending != null) {
                placeCall(pending)
            } else {
                message = "Calls need the phone permission."
            }
        }

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshDefault()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        number = numberFrom(intent)
        setContent {
            CallGuardTheme {
                DialScreen(
                    number = number,
                    isDefaultDialer = isDefaultDialer,
                    message = message,
                    onNumberChange = {
                        number = it
                        message = null
                    },
                    onCall = ::requestCall,
                    onSetDefault = ::requestDefault,
                )
            }
        }
        val placesNow = savedInstanceState == null && intent.action == Intent.ACTION_CALL && number.isNotBlank()
        if (placesNow) requestCall(number)
    }

    override fun onResume() {
        super.onResume()
        refreshDefault()
    }

    private fun refreshDefault() {
        isDefaultDialer = Dialer.isDefaultDialer(this)
    }

    /** Places the call when CALL_PHONE is granted, and asks for it first when it is not. */
    private fun requestCall(raw: String) {
        val digits = raw.trim()
        if (digits.isEmpty()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeCall(digits)
        } else {
            pendingCall = digits
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    /** Hands the call to Telecom and shows it on the in-call screen. Telecom refusing the call keeps this screen open. */
    private fun placeCall(raw: String) {
        if (Dialer.placeCall(this, raw)) {
            InCallActivity.start(this)
            finish()
        } else {
            message = "This number cannot be called."
        }
    }

    private fun requestDefault() {
        Dialer.requestRoleIntent(this)?.let { roleRequest.launch(it) }
    }

    companion object {
        /** The number an intent names: the decoded scheme-specific part of a tel: URI. Empty for any other intent. */
        fun numberFrom(intent: Intent?): String {
            val data: Uri = intent?.data ?: return ""
            if (!data.scheme.equals("tel", ignoreCase = true)) return ""
            return Uri.decode(data.schemeSpecificPart.orEmpty()).trim()
        }
    }
}
