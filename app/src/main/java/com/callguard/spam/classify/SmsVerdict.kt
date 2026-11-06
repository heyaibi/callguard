package com.callguard.spam.classify

/**
 * What an SMS is about. The priority that picks one kind is documented on [SmsClassifier].
 * The declaration order here is not that priority.
 */
enum class SmsKind { OTP, FINANCE, GOVERNMENT, TRANSACTIONAL, DELIVERY, PERSONAL, PROMOTIONAL, SCAM }

/** One contribution to a verdict. Positive weights push toward spam, negative weights toward ham. */
data class Signal(
    val name: String,
    val weight: Double,
)

/**
 * Result of [SmsClassifier.classify].
 *
 * @property spamScore a score from 0 to 1. Compare it with [ClassifierConfig.SPAM_THRESHOLD].
 * @property kind the one kind that the priority rules picked.
 * @property signals the largest contributions by absolute weight, at most [ClassifierConfig.MAX_SIGNALS].
 */
data class SmsVerdict(
    val spamScore: Double,
    val kind: SmsKind,
    val signals: List<Signal>,
)
