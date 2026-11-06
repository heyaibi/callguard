package com.callguard.spam.classify

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenModelTest {
    private fun trainedModel(): TokenModel {
        val model = TokenModel.empty()
        repeat(25) { i ->
            model.learn("AD-SYNTHX-S", "Your order $i has shipped", spam = false)
            model.learn("9000012345", "Win a prize now $i", spam = true)
        }
        return model
    }

    @Test
    fun serialisedModelReadsBackEqual() {
        val model = trainedModel()
        val bytes = model.toBytes()
        val restored = TokenModel.fromBytes(bytes)
        assertEquals(model, restored)
        assertEquals(model.hashCode(), restored.hashCode())
        assertArrayEquals(bytes, restored.toBytes())
    }

    @Test
    fun emptyModelRoundTrips() {
        val empty = TokenModel.empty()
        assertEquals(empty, TokenModel.fromBytes(empty.toBytes()))
    }

    @Test
    fun restoredModelScoresLikeTheOriginal() {
        val model = trainedModel()
        val original = SmsClassifier(model)
        val restored = SmsClassifier(TokenModel.fromBytes(model.toBytes()))
        val text = "Win a prize now and claim it"
        assertEquals(
            original.classify("9000012345", text).spamScore,
            restored.classify("9000012345", text).spamScore,
            0.0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownVersion() {
        val bytes = TokenModel.empty().toBytes()
        bytes[0] = 9
        TokenModel.fromBytes(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedBytes() {
        TokenModel.fromBytes(TokenModel.empty().toBytes().copyOf(2))
    }
}
