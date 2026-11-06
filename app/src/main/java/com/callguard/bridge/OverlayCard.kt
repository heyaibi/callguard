package com.callguard.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.callguard.R
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import kotlin.math.roundToInt

/**
 * Caller card drawn over another app with TYPE_APPLICATION_OVERLAY, styled like OverlayCardPreview in
 * ui/screens/IncomingCallScreen.kt. Plain Views, one card at a time, shown and dismissed on the main thread.
 * Without "display over other apps" it posts a heads-up notification on the caller_id channel instead.
 */
object OverlayCard {
    private const val DISMISS_MILLIS = 6_000L
    private const val FADE_MILLIS = 160L
    private const val CHANNEL_ID = "caller_id"
    private const val NOTIFICATION_ID = 4217

    // Same values as CgColors in ui/theme/Theme.kt. Views cannot read Compose colors.
    private val SURFACE = Color.parseColor("#1B1B1D")
    private val SURFACE_HIGH = Color.parseColor("#2A2A2D")
    private val DIVIDER = Color.parseColor("#1F1F21")
    private val WHITE = Color.parseColor("#FFFFFF")
    private val TEXT_DIM = Color.parseColor("#9A9A9E")
    private val RED = Color.parseColor("#D71921")

    private class Shown(
        val context: Context,
        val root: View,
    )

    private val main = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { dismiss(animate = true) }
    private var shown: Shown? = null

    /** True once the user has allowed CallGuard to draw over other apps. */
    fun canShow(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)

    /** Shows the card for six seconds, or until it is tapped. Safe to call from any thread. */
    fun show(
        context: Context,
        name: String,
        number: String,
        subtitle: String,
        spam: Boolean = false,
    ) {
        val app = context.applicationContext
        onMain {
            if (!canShow(app)) {
                notifyFallback(app, name, number, subtitle, spam)
                return@onMain
            }
            dismiss(animate = false)
            val card = buildCard(app, name, number, subtitle, spam)
            val root =
                FrameLayout(app).apply {
                    val gutter = app.dp(16f)
                    setPadding(gutter, 0, gutter, 0)
                    addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                }
            card.setOnClickListener { if (shown?.root === root) dismiss(animate = true) }
            try {
                app.getSystemService(WindowManager::class.java).addView(root, layoutParams(app))
            } catch (e: RuntimeException) {
                // the window was refused, for example BadTokenException
                notifyFallback(app, name, number, subtitle, spam)
                return@onMain
            }
            shown = Shown(app, root)
            root.alpha = 0f
            root
                .animate()
                .alpha(1f)
                .setDuration(FADE_MILLIS)
                .start()
            main.postDelayed(autoDismiss, DISMISS_MILLIS)
        }
    }

    private fun dismiss(animate: Boolean) {
        main.removeCallbacks(autoDismiss)
        val current = shown ?: return
        shown = null
        if (animate) {
            current.root
                .animate()
                .alpha(0f)
                .setDuration(FADE_MILLIS)
                .withEndAction { removeRoot(current) }
                .start()
        } else {
            removeRoot(current)
        }
    }

    private fun removeRoot(current: Shown) {
        try {
            current.context.getSystemService(WindowManager::class.java).removeView(current.root)
        } catch (e: IllegalArgumentException) {
            // Already removed, for example after the overlay permission was revoked.
        }
    }

    private fun layoutParams(context: Context): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP
                y = statusBarHeight(context) + context.dp(8f)
                title = "CallGuardCard"
            }

    private fun buildCard(
        context: Context,
        name: String,
        number: String,
        subtitle: String,
        spam: Boolean,
    ): LinearLayout {
        val res = context.resources

        val avatar =
            FrameLayout(context).apply {
                background = panel(context, SURFACE_HIGH, radiusDp = 17f, strokeDp = 1.5f, strokeColor = WHITE)
                addView(
                    TextView(context).apply {
                        text = initialsOf(name).uppercase()
                        typeface = res.getFont(R.font.doto_black)
                        textSize = 13f
                        letterSpacing = 0.08f
                        setTextColor(WHITE)
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
            }

        val labels =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply {
                        text = name.uppercase()
                        typeface = res.getFont(R.font.doto_black)
                        textSize = 18f
                        letterSpacing = 0.055f
                        setTextColor(WHITE)
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                )
                addView(
                    TextView(context).apply {
                        text = listOf(subtitle.uppercase(), displayNumber(number)).filter { it.isNotBlank() }.joinToString(" · ")
                        typeface = res.getFont(R.font.space_mono_regular)
                        textSize = 11f
                        letterSpacing = 0.14f
                        setTextColor(TEXT_DIM)
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = context.dp(4f)
                    },
                )
            }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = panel(context, SURFACE, radiusDp = 24f, strokeDp = 1f, strokeColor = DIVIDER)
            val pad = context.dp(14f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            contentDescription = "Incoming call from $name" + (if (spam) ", likely spam" else "")
            addView(avatar, LinearLayout.LayoutParams(context.dp(52f), context.dp(34f)).apply { marginEnd = context.dp(12f) })
            addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (spam) {
                addView(
                    View(context).apply { background = panel(context, RED, radiusDp = 4f) },
                    LinearLayout.LayoutParams(context.dp(8f), context.dp(8f)).apply { marginStart = context.dp(8f) },
                )
            }
        }
    }

    private fun panel(
        context: Context,
        fill: Int,
        radiusDp: Float,
        strokeDp: Float = 0f,
        strokeColor: Int = Color.TRANSPARENT,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpF(radiusDp)
            setColor(fill)
            if (strokeDp > 0f) setStroke(context.dpF(strokeDp).roundToInt().coerceAtLeast(1), strokeColor)
        }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else context.dp(24f)
    }

    /** Heads-up notification used when the card cannot be drawn. The post is dropped when notifications are not allowed. */
    private fun notifyFallback(
        context: Context,
        name: String,
        number: String,
        subtitle: String,
        spam: Boolean,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Caller ID", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Name cards for calls from other apps" },
            )
        }
        val details =
            listOfNotNull(
                if (spam) "LIKELY SPAM" else null,
                subtitle.uppercase(),
                displayNumber(number),
            ).joinToString(" · ")
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(name)
                .setContentText(details)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(DISMISS_MILLIS)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private fun Context.dpF(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun Context.dp(value: Float): Int = dpF(value).roundToInt()
}
