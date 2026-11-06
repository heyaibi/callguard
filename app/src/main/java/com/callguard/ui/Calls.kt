package com.callguard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.callguard.call.Dialer

/**
 * Returns the function every call button uses. It asks for CALL_PHONE the first time, then places the call.
 * The only place the UI starts a call.
 */
@Composable
fun rememberPlaceCall(): (String) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<String?>(null) }
    // Placed whether or not the permission was granted: without it, placeOrDial falls back to the system dialer.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val number = waiting
        waiting = null
        if (number != null) placeOrDial(context, number)
    }
    return remember {
        { number: String ->
            if (number.isNotEmpty()) {
                if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    placeOrDial(context, number)
                } else {
                    waiting = number
                    permission.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }
}

/**
 * Places the call through Telecom. When Telecom refuses (no permission, no calling account), the system dialer
 * opens with the number, so the user can still call.
 */
private fun placeOrDial(context: Context, number: String) {
    if (!Dialer.placeCall(context, number)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
    }
}
