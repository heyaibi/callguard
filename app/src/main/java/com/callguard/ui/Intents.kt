package com.callguard.ui

import android.content.Context
import android.content.Intent

/** Starts [intent] and ignores the failure when no app can handle it. */
internal fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent) }
}

/** Opens the share sheet with [text] as plain text. */
internal fun Context.shareText(text: String) {
    startSafely(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}
