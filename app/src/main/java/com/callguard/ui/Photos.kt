package com.callguard.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded contact thumbnails, keyed by their content URI. */
private object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun get(uri: String): ImageBitmap? = cache.get(uri)

    fun put(
        uri: String,
        image: ImageBitmap,
    ) {
        cache.put(uri, image)
    }
}

/**
 * The thumbnail for a contact photo URI, or null while it loads or when there is none.
 * The bitmap is read on IO and kept in [PhotoCache], so scrolling does not decode it twice.
 */
@Composable
fun rememberContactPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val photo by produceState(initialValue = uri?.let(PhotoCache::get), uri) {
        if (uri == null) {
            value = null
            return@produceState
        }
        value = PhotoCache.get(uri) ?: withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.asImageBitmap()
        }?.also { PhotoCache.put(uri, it) }
    }
    return photo
}
