package com.callguard.spam.classify

import java.io.ByteArrayOutputStream
import kotlin.math.ln

/**
 * Multinomial Naive Bayes over the hashed features of a message (see [TokenFeatures]), with Laplace smoothing.
 *
 * The model learns one message at a time with [learn]. [toBytes] and [fromBytes] store it in a compact format.
 * The model is mutable and not thread-safe.
 */
class TokenModel private constructor(
    private val spamCounts: IntArray,
    private val hamCounts: IntArray,
    private var spamDocs: Int,
    private var hamDocs: Int,
) {
    private var spamTokens: Long = spamCounts.sumOf { it.toLong() }
    private var hamTokens: Long = hamCounts.sumOf { it.toLong() }

    /** Number of messages learned so far, spam and ham together. */
    val docs: Int get() = spamDocs + hamDocs

    /** Learns one labelled message. */
    fun learn(
        sender: String,
        body: String,
        spam: Boolean,
    ) {
        add(TokenFeatures.of(SenderParser.parse(sender), TextNormalizer.normalize(body)), spam)
    }

    internal fun add(
        features: IntArray,
        spam: Boolean,
    ) {
        val counts = if (spam) spamCounts else hamCounts
        for (f in features) {
            counts[f]++
        }
        if (spam) {
            spamDocs++
            spamTokens += features.size
        } else {
            hamDocs++
            hamTokens += features.size
        }
    }

    /** Natural log of the spam-to-ham odds of these features. Zero when nothing has been learned. */
    internal fun logOdds(features: IntArray): Double {
        val vocabulary = TokenFeatures.BUCKETS.toDouble()
        val spamDenominator = ln(spamTokens + vocabulary)
        val hamDenominator = ln(hamTokens + vocabulary)
        var total = ln((spamDocs + 1.0) / (hamDocs + 1.0))
        for (f in features) {
            total += ln(spamCounts[f] + 1.0) - spamDenominator
            total -= ln(hamCounts[f] + 1.0) - hamDenominator
        }
        return total
    }

    /** Serialises the model: a version byte, the two document counts, then each non-empty bucket as varints. */
    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(FORMAT_VERSION)
        out.writeVarint(spamDocs)
        out.writeVarint(hamDocs)
        val used = (0 until TokenFeatures.BUCKETS).filter { spamCounts[it] != 0 || hamCounts[it] != 0 }
        out.writeVarint(used.size)
        var previous = 0
        for (index in used) {
            out.writeVarint(index - previous)
            out.writeVarint(spamCounts[index])
            out.writeVarint(hamCounts[index])
            previous = index
        }
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenModel) return false
        if (spamDocs != other.spamDocs || hamDocs != other.hamDocs) return false
        return spamCounts.contentEquals(other.spamCounts) && hamCounts.contentEquals(other.hamCounts)
    }

    override fun hashCode(): Int {
        val countsHash = 31 * spamCounts.contentHashCode() + hamCounts.contentHashCode()
        return 31 * (31 * spamDocs + hamDocs) + countsHash
    }

    companion object {
        private const val FORMAT_VERSION = 1

        /** A model that has learned nothing. The rules alone decide until it has learned enough messages. */
        fun empty(): TokenModel = TokenModel(IntArray(TokenFeatures.BUCKETS), IntArray(TokenFeatures.BUCKETS), 0, 0)

        /** Reads a model written by [toBytes]. Throws IllegalArgumentException when the bytes are not a model. */
        fun fromBytes(bytes: ByteArray): TokenModel {
            val reader = VarintReader(bytes)
            require(reader.byte() == FORMAT_VERSION) { "Unknown token model version" }
            val spamDocs = reader.varint()
            val hamDocs = reader.varint()
            val spamCounts = IntArray(TokenFeatures.BUCKETS)
            val hamCounts = IntArray(TokenFeatures.BUCKETS)
            var index = 0
            repeat(reader.varint()) {
                index += reader.varint()
                require(index < TokenFeatures.BUCKETS) { "Token model bucket out of range" }
                spamCounts[index] = reader.varint()
                hamCounts[index] = reader.varint()
            }
            require(reader.atEnd()) { "Unexpected bytes after the token model" }
            return TokenModel(spamCounts, hamCounts, spamDocs, hamDocs)
        }
    }
}

private const val BYTE_MASK = 0xFF
private const val VALUE_MASK = 0x7F
private const val CONTINUE_BIT = 0x80
private const val VALUE_BITS = 7
private const val MAX_SHIFT = 35

private fun ByteArrayOutputStream.writeVarint(value: Int) {
    var rest = value
    while (rest >= CONTINUE_BIT) {
        write((rest and VALUE_MASK) or CONTINUE_BIT)
        rest = rest ushr VALUE_BITS
    }
    write(rest)
}

private class VarintReader(
    private val bytes: ByteArray,
) {
    private var pos = 0

    fun byte(): Int {
        require(pos < bytes.size) { "Truncated token model" }
        return bytes[pos++].toInt() and BYTE_MASK
    }

    fun varint(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = byte()
            result = result or ((b and VALUE_MASK) shl shift)
            if ((b and CONTINUE_BIT) == 0) return result
            shift += VALUE_BITS
            require(shift < MAX_SHIFT) { "Token model varint is too long" }
        }
    }

    fun atEnd(): Boolean = pos == bytes.size
}
