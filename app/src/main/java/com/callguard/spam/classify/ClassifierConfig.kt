package com.callguard.spam.classify

/**
 * Every tuning constant of the SMS classifier.
 *
 * Rule weights add up on the logit scale: the spam score is sigmoid(BIAS + sum of rule weights + token term).
 * A positive weight pushes toward spam and a negative weight toward ham. A weight of 1.0 multiplies the spam
 * odds by about 2.7.
 */
object ClassifierConfig {
    /** A spam score at or above this value counts as spam. */
    const val SPAM_THRESHOLD = 0.6

    /** Logit with no signal. sigmoid(-1.0) is about 0.27, so an unknown message is not spam. */
    const val BIAS = -1.0

    /** The token model adds nothing until it has learned this many messages. */
    const val NB_MIN_DOCS = 20

    /** Scales the clamped token-model log-odds before they join the logit. */
    const val NB_WEIGHT = 0.5

    /** Limits the token-model log-odds to plus or minus this value, before scaling. */
    const val NB_CLAMP = 4.0

    /** Highest spam score for a protected message that has no scam signal. */
    const val SAFETY_CAP = 0.2

    /** A message with a scam signal becomes SCAM at this score or higher. */
    const val SCAM_KIND_SCORE = 0.7

    /** A message with no promotional marker becomes PROMOTIONAL at this score or higher. */
    const val PROMO_KIND_SCORE = 0.5

    /** This many promotional words make a message promotional, whatever its score. */
    const val PROMO_KIND_WORDS = 3

    /** Promotional words add weight, up to this many words. */
    const val PROMO_WORD_CAP = 3

    /** Keywords of this length or longer also match with one edit (insert, delete or replace). */
    const val FUZZY_MIN_LENGTH = 5

    /** Longest host label that counts as a very short, shortener-like domain. */
    const val SHORT_LABEL_MAX = 4

    /**
     * Characters before a KYC, block or update phrase that are checked for a negation, such as "never". The search also
     * stops at the start of the sentence. One SMS segment is 160 characters, so a whole safety-notice sentence fits.
     */
    const val NEGATION_WINDOW = 160

    /** A verdict lists at most this many signals. */
    const val MAX_SIGNALS = 5

    // Sender rules.
    const val W_DLT_TRANSACTIONAL = -1.2
    const val W_DLT_SERVICE = -1.0
    const val W_DLT_GOVERNMENT = -1.5
    const val W_DLT_PROMOTIONAL = 0.8
    const val W_GOV_HEADER = -2.0
    const val W_FINANCE_HEADER = -0.5
    const val W_MOBILE_SENDER = -0.4
    const val W_MOBILE_URL = 2.5

    // Content rules.
    const val W_OTP = -3.0
    const val W_FINANCE_ALERT = -1.5
    const val W_SERVICE_ALERT = -1.0
    const val W_DELIVERY = -1.2
    const val W_URL = 0.4
    const val W_URL_SHORTENER = 2.0

    /** A shortener link from a DLT header. It weighs as much as one promotional word. */
    const val W_DLT_SHORTENER = 0.5
    const val W_KYC = 2.5
    const val W_UPI_COLLECT = 3.0
    const val W_CREDIT_OFFER = 2.0
    const val W_PRIZE = 2.5
    const val W_BETTING = 2.5
    const val W_PROMO_WORD = 0.5
}
