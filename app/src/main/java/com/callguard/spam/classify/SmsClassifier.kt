package com.callguard.spam.classify

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * On-device SMS spam classifier.
 *
 * Rules score the sender and the text. After [ClassifierConfig.NB_MIN_DOCS] messages have been learned, a
 * token model adds a bounded term.
 *
 * Kind, first match wins: OTP, GOVERNMENT, FINANCE, TRANSACTIONAL, DELIVERY, SCAM, PROMOTIONAL.
 * Any other message from a DLT header or a short code is TRANSACTIONAL. Only mobile and other senders are PERSONAL.
 * FINANCE, TRANSACTIONAL and DELIVERY also need a message that is not promotional and has no scam signal.
 * A promotional message is one with suffix P, a credit or loan offer, or several promotional words.
 *
 * Safety gate: an OTP, or a DLT message with suffix T, S or G that has a finance, service, government or
 * delivery signal, scores at most [ClassifierConfig.SAFETY_CAP], unless a scam signal is also present.
 */
class SmsClassifier(
    private val model: TokenModel = TokenModel.empty(),
) {
    /** Scores one message. [sender] is the address as the phone reports it. */
    fun classify(
        sender: String,
        body: String,
    ): SmsVerdict {
        val info = SenderParser.parse(sender)
        val sms = TextNormalizer.normalize(body)
        val rules = Rules.evaluate(info, sms)
        val modelSignal = modelSignal(info, sms)
        val logit = ClassifierConfig.BIAS + rules.hits.sumOf { it.weight } + (modelSignal?.weight ?: 0.0)
        var score = sigmoid(logit)
        if (rules.gated && !rules.scam) score = min(score, ClassifierConfig.SAFETY_CAP)
        val signals =
            (rules.hits.map { Signal(it.name, it.weight) } + listOfNotNull(modelSignal))
                .sortedByDescending { abs(it.weight) }
                .take(ClassifierConfig.MAX_SIGNALS)
        return SmsVerdict(score, kindOf(info, rules, score), signals)
    }

    /** Teaches the token model one labelled message. The rules do not change. */
    fun learn(
        sender: String,
        body: String,
        spam: Boolean,
    ) {
        model.learn(sender, body, spam)
    }

    private fun modelSignal(
        info: SenderInfo,
        sms: NormalizedSms,
    ): Signal? {
        if (model.docs < ClassifierConfig.NB_MIN_DOCS) return null
        val raw = model.logOdds(TokenFeatures.of(info, sms))
        val clamped = raw.coerceIn(-ClassifierConfig.NB_CLAMP, ClassifierConfig.NB_CLAMP)
        return Signal("token_model", clamped * ClassifierConfig.NB_WEIGHT)
    }

    private fun kindOf(
        sender: SenderInfo,
        rules: RuleResult,
        score: Double,
    ): SmsKind =
        protectedKind(rules) ?: when {
            rules.scam && score >= ClassifierConfig.SCAM_KIND_SCORE -> SmsKind.SCAM
            rules.promotional || score >= ClassifierConfig.PROMO_KIND_SCORE -> SmsKind.PROMOTIONAL
            sender.kind == SenderKind.DLT || sender.kind == SenderKind.SHORT_CODE -> SmsKind.TRANSACTIONAL
            else -> SmsKind.PERSONAL
        }

    /** OTP, GOVERNMENT, FINANCE, TRANSACTIONAL and DELIVERY, in that order. Null when none of them applies. */
    private fun protectedKind(rules: RuleResult): SmsKind? {
        val serviceAllowed = !rules.scam && !rules.promotional
        return when {
            rules.otp -> SmsKind.OTP
            rules.government -> SmsKind.GOVERNMENT
            serviceAllowed && rules.finance -> SmsKind.FINANCE
            serviceAllowed && rules.service -> SmsKind.TRANSACTIONAL
            serviceAllowed && rules.delivery -> SmsKind.DELIVERY
            else -> null
        }
    }

    private fun sigmoid(logit: Double): Double = 1.0 / (1.0 + exp(-logit))
}
