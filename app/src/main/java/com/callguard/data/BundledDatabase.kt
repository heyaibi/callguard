package com.callguard.data

import android.content.Context
import com.callguard.core.PhoneNumber
import org.json.JSONArray
import org.json.JSONObject

/** Read-only business and spam numbers from assets/bundled.json. An unreadable file yields an empty database. */
class BundledDatabase(context: Context) {
    val version: Int
    private val business: Map<String, String>
    private val spam: Set<String>

    init {
        val root = runCatching {
            JSONObject(context.assets.open("bundled.json").bufferedReader().use { it.readText() })
        }.getOrElse { JSONObject() }
        version = root.optInt("version", 0)
        val names = root.optJSONObject("business") ?: JSONObject()
        business = names.keys().asSequence().associateWith { names.getString(it) }
        val numbers = root.optJSONArray("spam") ?: JSONArray()
        spam = (0 until numbers.length()).map { numbers.getString(it) }.toSet()
    }

    fun nameFor(n: PhoneNumber): String? = business[n.e164]

    fun isSpam(n: PhoneNumber): Boolean = n.e164 in spam
}
