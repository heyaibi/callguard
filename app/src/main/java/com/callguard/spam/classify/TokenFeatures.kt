package com.callguard.spam.classify

/**
 * Hashed features for [TokenModel]: words, word pairs, character 3 to 5 grams of the masked text, and one
 * token for the sender type.
 */
internal object TokenFeatures {
    /** Number of feature buckets. */
    const val BUCKETS = 65536

    private const val MASK = BUCKETS - 1
    private const val SHIFT = 16
    private const val PRIME = 16777619
    private const val MIN_GRAM = 3
    private const val MAX_GRAM = 5
    private const val SEED_WORD = 0x2A41
    private const val SEED_PAIR = 0x3B52
    private const val SEED_GRAM = 0x4C63
    private const val SEED_SENDER = 0x5D74

    fun of(
        sender: SenderInfo,
        sms: NormalizedSms,
    ): IntArray {
        val text = sms.masked
        val words = text.split(' ').filter { it.isNotEmpty() }
        val gramCount = (MIN_GRAM..MAX_GRAM).sumOf { maxOf(0, text.length - it + 1) }
        val senderToken = "${sender.kind}:${sender.suffix ?: '-'}"
        val out = IntArray(words.size + maxOf(0, words.size - 1) + gramCount + 1)
        var k = 0
        for (w in words) {
            out[k++] = hash(SEED_WORD, w, 0, w.length)
        }
        for (i in 1 until words.size) {
            val pair = words[i - 1] + " " + words[i]
            out[k++] = hash(SEED_PAIR, pair, 0, pair.length)
        }
        for (n in MIN_GRAM..MAX_GRAM) {
            for (i in 0..text.length - n) {
                out[k++] = hash(SEED_GRAM, text, i, i + n)
            }
        }
        out[k] = hash(SEED_SENDER, senderToken, 0, senderToken.length)
        return out
    }

    private fun hash(
        seed: Int,
        text: String,
        from: Int,
        to: Int,
    ): Int {
        var h = seed
        for (i in from until to) {
            h = (h xor text[i].code) * PRIME
        }
        return (h xor (h ushr SHIFT)) and MASK
    }
}
