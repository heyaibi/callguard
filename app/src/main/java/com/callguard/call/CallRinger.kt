package com.callguard.call

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Plays the ringtone and vibration for an incoming call. CallGuard rings itself because the in-call service declares
 * IN_CALL_SERVICE_RINGING, which turns off the system ringer. Main thread only.
 */
object CallRinger {
    /** Vibrate for one second, pause for one second, and repeat until stopped. */
    private val PATTERN = longArrayOf(0L, 1000L, 1000L)

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var current: RingPlan? = null

    /** Starts the ringing described by [plan]. A call with the same plan already ringing is left alone. */
    fun start(context: Context, plan: RingPlan) {
        if (current == plan) return
        stop()
        current = plan
        runCatching {
            if (plan.sound) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = uri?.let { RingtoneManager.getRingtone(context, it) }?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    isLooping = true
                    play()
                }
            }
            if (plan.vibrate) {
                vibrator = context.getSystemService(Vibrator::class.java)?.also {
                    it.vibrate(VibrationEffect.createWaveform(PATTERN, 0))
                }
            }
        }
    }

    /** Stops the sound and the vibration. Safe to call when nothing is ringing. */
    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        current = null
    }
}
