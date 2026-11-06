package com.callguard.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives MMS push notifications so CallGuard can hold the default SMS role. MMS download is not built,
 * so the push is ignored.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit
}
