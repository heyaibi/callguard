package com.callguard.spam.classify

import kotlin.math.abs

/** One rule that fired. [scam] marks the signals that lift the safety gate and allow the SCAM kind. */
internal data class RuleHit(
    val name: String,
    val weight: Double,
    val scam: Boolean = false,
)

/** Rule output for one message. The flags are the kind groups that [SmsClassifier] reads in priority order. */
internal data class RuleResult(
    val hits: List<RuleHit>,
    val otp: Boolean,
    val government: Boolean,
    val finance: Boolean,
    val service: Boolean,
    val delivery: Boolean,
    val promotional: Boolean,
    val scam: Boolean,
    val gated: Boolean,
)

/** The rule set. Weights and thresholds come from [ClassifierConfig]. */
internal object Rules {
    private val otpWordRe =
        Regex(
            """\b(?:otp|one[ -]?time (?:password|passcode|pass code)|verification code|verify code|""" +
                """security code|login code|passcode)\b""",
        )
    private val codeRe = Regex("""(?<![\d.,])\d{4,8}(?!\d|[.,]\d)""")
    private val letterWordRe = Regex("""\p{L}+""")
    private val financeRe =
        Regex(
            """\b(?:trade executed|contract note|mutual fund|(?:debit|credit) card|a/c|avl bal|available bal|""" +
                """txn (?:id|no|ref)|transaction (?:of|id|no|ref|successful|failed))\b|\bending (?:in )?<num>""",
        )
    private val serviceRe =
        Regex(
            """\bbill\b.{0,40}\b(?:due|paid|payment|generated|overdue|amount)\b|""" +
                """\b(?:electricity|water|gas|broadband|postpaid|dth|utility) (?:bill|payment|due)\b|""" +
                """\brecharge (?:of|is|was|successful|successfully|done|failed)\b|\bpnr\b|""" +
                """\bbooking (?:confirmed|id|no)\b|\b(?:order|appointment) (?:confirmed|booked|scheduled)\b""",
        )
    private val deliveryRe =
        Regex(
            """\bout for (?:delivery|pickup|pick up)\b|""" +
                """\btrack(?:ing)? (?:your|the|order|shipment|parcel|package)\b|""" +
                """\byour (?:order|package|parcel|shipment)\b|""" +
                """\bdelivery (?:agent|partner|boy|executive|slot)\b""",
        )
    private val creditOfferRe =
        Regex(
            """\bpre[- ]?approved\b|\binstant loans?\b|\bloans? (?:offer|approved|sanctioned|available)\b|""" +
                """\bloans? at <num>|\bget (?:a |an |your )?(?:instant |personal )?(?:loans?|credit cards?)\b|""" +
                """\bcredit cards? (?:offer|upgrade|limit)\b|\bcredit limit (?:offer|increase|enhanced|approved)\b|""" +
                """\bapply (?:now )?(?:for|at) (?:a |your )?(?:credit card|loan)\b|\blow interest\b""",
        )
    private val kycRe =
        Regex(
            """\bkyc\b|\bupdate (?:your )?pan\b|""" +
                """\b(?:account|a/c|sim|wallet|upi|card)\b.{0,40}""" +
                """\b(?:blocked|suspended|deactivated|frozen)\b.{0,60}""" +
                """\b(?:update|verify|click|reactivate|unblock|link|now|immediately|today)\b""",
        )
    private val negationRe = Regex("""\b(?:never|do not share|don['’]t share|will not ask|won['’]t ask)\b""")
    private val upiCollectRe =
        Regex(
            """\bcollect (?:request|req)\b|""" +
                """\b(?:accept|approve|confirm) (?:the |this |a )?(?:money|payment|request|collect)\b|""" +
                """\bto receive (?:money|payment|<amt>)\b|\b(?:enter|type) (?:your )?upi (?:pin|mpin)\b""",
        )
    private val prizeRe =
        Regex(
            """\b(?:lottery|lucky draw|jackpot|prize|winner)\b|\byou (?:have |are )?(?:won|a winner)\b|""" +
                """\bclaim (?:your |the )?(?:cashback|reward|gift|prize)\b""",
        )
    private val bettingRe =
        Regex(
            """\b(?:betting|casino|satta|rummy|teen patti|online gaming)\b|""" +
                """\bearn\b.{0,25}\b(?:per (?:day|hour|week|month)|daily|from home|weekly)\b|""" +
                """\bwork from home\b|\bwfh\b|\bpart[- ]?time job\b""",
        )
    private val govWords = words("uidai aadhaar aadhar incometax itdept epfo gov cowin digilocker mygov passport")
    private val govExact = setOf("nic")
    private val financeBrands =
        words(
            "bank hdfc icici sbi axis kotak idfc yesb indus pnb canara federal rbl idbi zerodha groww upstox " +
                "angel dhan kite cdsl nsdl",
        )
    private val financeExact = setOf("nse", "bse")
    private val financeWords =
        words(
            "debited credited upi neft imps rtgs utr demat cdsl nsdl nse bse sip margin brokerage acct txn avl",
        )
    private val deliveryWords = words("shipped dispatched delivered courier awb parcel shipment")
    private val promoWords =
        words(
            "offer offers sale discount hurry limited click subscribe exclusive coupon deal deals free flat bumper off",
        )
    private val shorteners =
        words(
            "bit.ly tinyurl.com goo.gl t.co is.gd cutt.ly rb.gy ow.ly tiny.cc bit.do t.ly v.gd shorturl.at " +
                "rebrand.ly buff.ly dub.co bl.ink lnkd.in fb.me amzn.to urlz.at clck.ru soo.gd tr.ee u.to",
        ).toSet()

    /** Runs every rule over one sender and one normalised message. */
    fun evaluate(
        sender: SenderInfo,
        sms: NormalizedSms,
    ): RuleResult {
        val f = facts(sender, sms)
        val hits =
            listOfNotNull(
                rule("otp_code", ClassifierConfig.W_OTP, f.otp),
                rule("dlt_transactional", ClassifierConfig.W_DLT_TRANSACTIONAL, sender.suffix == 'T'),
                rule("dlt_service", ClassifierConfig.W_DLT_SERVICE, sender.suffix == 'S'),
                rule("dlt_government", ClassifierConfig.W_DLT_GOVERNMENT, sender.suffix == 'G'),
                rule("dlt_promotional", ClassifierConfig.W_DLT_PROMOTIONAL, sender.suffix == 'P'),
                rule("gov_header", ClassifierConfig.W_GOV_HEADER, f.govHeader),
                rule("finance_header", ClassifierConfig.W_FINANCE_HEADER, f.financeHeader),
                rule("finance_alert", ClassifierConfig.W_FINANCE_ALERT, f.financeAlert),
                rule("service_alert", ClassifierConfig.W_SERVICE_ALERT, f.service),
                rule("delivery_update", ClassifierConfig.W_DELIVERY, f.delivery),
                rule("mobile_sender", ClassifierConfig.W_MOBILE_SENDER, f.mobile),
                rule("url_link", ClassifierConfig.W_URL, f.hasUrl),
                rule("url_shortener", ClassifierConfig.W_URL_SHORTENER, f.shortenerScam, scam = true),
                rule("url_shortener_dlt", ClassifierConfig.W_DLT_SHORTENER, f.shortenerDlt),
                rule("mobile_sender_url", ClassifierConfig.W_MOBILE_URL, f.mobile && f.hasUrl, scam = true),
                rule("kyc_or_blocked_account", ClassifierConfig.W_KYC, f.kyc, scam = true),
                rule("upi_collect_request", ClassifierConfig.W_UPI_COLLECT, f.upiCollect, scam = true),
                rule("credit_offer", ClassifierConfig.W_CREDIT_OFFER, f.creditOffer),
                rule("prize_claim", ClassifierConfig.W_PRIZE, f.prize, scam = true),
                rule("betting_or_earning", ClassifierConfig.W_BETTING, f.betting),
                rule(
                    "promo_words",
                    ClassifierConfig.W_PROMO_WORD * minOf(f.promoCount, ClassifierConfig.PROMO_WORD_CAP),
                    f.promoCount > 0,
                ),
            )
        val government = f.govHeader || sender.suffix == 'G'
        val finance = f.financeHeader || f.financeAlert
        val protectedSuffix = sender.suffix == 'T' || sender.suffix == 'S' || sender.suffix == 'G'
        val promotional = sender.suffix == 'P' || f.creditOffer || f.promoCount >= ClassifierConfig.PROMO_KIND_WORDS
        return RuleResult(
            hits = hits,
            otp = f.otp,
            government = government,
            finance = finance,
            service = f.service,
            delivery = f.delivery,
            promotional = promotional,
            scam = hits.any { it.scam },
            gated = f.otp || (protectedSuffix && (finance || f.service || government || f.delivery)),
        )
    }

    private fun rule(
        name: String,
        weight: Double,
        fired: Boolean,
        scam: Boolean = false,
    ): RuleHit? = if (fired) RuleHit(name, weight, scam) else null

    private fun facts(
        sender: SenderInfo,
        sms: NormalizedSms,
    ): Facts {
        val text = sms.masked
        val words = letterWordRe.findAll(text).map { it.value }.toList()
        val brand = sender.name.lowercase()
        return Facts(
            otp = otpWordRe.containsMatchIn(sms.folded) && codeRe.containsMatchIn(sms.folded),
            govHeader = govWords.any { brand.contains(it) } || brand in govExact,
            financeHeader = financeBrands.any { brand.contains(it) } || brand in financeExact,
            financeAlert = anyFuzzy(words, financeWords) || financeRe.containsMatchIn(text),
            service = serviceRe.containsMatchIn(text),
            delivery = anyFuzzy(words, deliveryWords) || deliveryRe.containsMatchIn(text),
            creditOffer = creditOfferRe.containsMatchIn(text),
            kyc = kycFires(text),
            upiCollect = upiCollectRe.containsMatchIn(text),
            prize = prizeRe.containsMatchIn(text),
            betting = bettingRe.containsMatchIn(text),
            promoCount = words.count { w -> promoWords.any { fuzzyEq(w, it) } },
            hasUrl = sms.urlHosts.isNotEmpty(),
            shortenerScam = sms.urlHosts.any { isShortener(it) } && sender.kind != SenderKind.DLT,
            shortenerDlt = sms.urlHosts.any { isShortener(it) } && sender.kind == SenderKind.DLT,
            mobile = sender.kind == SenderKind.MOBILE,
        )
    }

    private fun anyFuzzy(
        words: List<String>,
        keywords: List<String>,
    ): Boolean = words.any { w -> keywords.any { fuzzyEq(w, it) } }

    private fun fuzzyEq(
        word: String,
        keyword: String,
    ): Boolean {
        if (word == keyword) return true
        val longEnough = keyword.length >= ClassifierConfig.FUZZY_MIN_LENGTH
        return longEnough && withinOneEdit(word, keyword)
    }

    /** True when the two strings differ by at most one insert, delete or replace. */
    private fun withinOneEdit(
        a: String,
        b: String,
    ): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length && edits <= 1) {
            if (a[i] == b[j]) {
                i++
                j++
            } else {
                edits++
                when {
                    a.length > b.length -> {
                        i++
                    }

                    a.length < b.length -> {
                        j++
                    }

                    else -> {
                        i++
                        j++
                    }
                }
            }
        }
        val rest = (a.length - i) + (b.length - j)
        return edits + rest <= 1
    }

    /** True for a known shortener host, or for a very short two-label host whose top-level domain is not .in. */
    private fun isShortener(host: String): Boolean {
        if (host in shorteners) return true
        val labels = host.split('.')
        val veryShort = labels.size == 2 && labels[0].length <= ClassifierConfig.SHORT_LABEL_MAX
        return veryShort && labels[1].length == 2 && labels[1] != "in"
    }

    /** True when a KYC, block or update phrase appears and no negation comes before it in the same sentence. */
    private fun kycFires(text: String): Boolean = kycRe.findAll(text).any { m -> !isNegated(text, m.range.first) }

    private fun isNegated(
        text: String,
        start: Int,
    ): Boolean {
        val before = text.substring(maxOf(0, start - ClassifierConfig.NEGATION_WINDOW), start)
        val sentence = before.split('.', '!', '?').last()
        return negationRe.containsMatchIn(sentence)
    }

    private fun words(text: String): List<String> = text.split(' ')
}

/** Facts about one message, computed once and read by [Rules.evaluate]. */
private data class Facts(
    val otp: Boolean,
    val govHeader: Boolean,
    val financeHeader: Boolean,
    val financeAlert: Boolean,
    val service: Boolean,
    val delivery: Boolean,
    val creditOffer: Boolean,
    val kyc: Boolean,
    val upiCollect: Boolean,
    val prize: Boolean,
    val betting: Boolean,
    val promoCount: Int,
    val hasUrl: Boolean,
    val shortenerScam: Boolean,
    val shortenerDlt: Boolean,
    val mobile: Boolean,
)
