package com.callguard.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Holds the call in progress as a foreground service, so its CallStyle notification may be ongoing. The platform
 * accepts an ongoing CallStyle notification only from a foreground service. [CallManager] starts and stops it. Each
 * start rebuilds the notification from the current call state, which also updates it.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CallNotifier.createChannels(this)
        val call = CallManager.inProgressCall()
        // startForeground must follow every startForegroundService call, even when no call is left by then.
        val notification = if (call != null) CallNotifier.ongoing(this, call, callStyle = true) else CallNotifier.ended(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(CallNotifier.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(CallNotifier.ONGOING_ID, notification)
        }
        if (call == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        /**
         * Starts the service, or refreshes its notification when it already runs. Throws when the platform refuses the
         * start, for example from the background.
         */
        fun start(context: Context) {
            CallNotifier.createChannels(context)
            context.startForegroundService(Intent(context, CallForegroundService::class.java))
        }

        /** Stops the service and removes its notification. Safe when it is not running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
