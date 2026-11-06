package com.callguard.spam.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic messages only. Senders and bodies are written for these tests. */
class SmsClassifierTest {
    private val classifier = SmsClassifier()

    @Test
    fun otpFromDltHeaderIsOtpWithLowScore() {
        val verdict =
            classifier.classify(
                "BZ-SYNTHX-T",
                "Your one time password is 482913. Do not share it with anyone.",
            )
        assertEquals(SmsKind.OTP, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore < 0.2)
    }

    @Test
    fun bankOtpStaysOtp() {
        val verdict =
            classifier.classify(
                "VM-SYNTHBANK-S",
                "Your OTP for net banking login is 482913. Valid for 5 minutes. Do not share. A/c ending 1234.",
            )
        assertEquals(SmsKind.OTP, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore < 0.2)
    }

    @Test
    fun bankDebitAlertIsFinance() {
        val verdict =
            classifier.classify(
                "VM-SYNTHBANK-T",
                "Rs 1,250.00 debited from A/c XX1234 on 05-11-26. UPI ref 123456789012. " +
                    "Avl bal Rs 9,876.50. Call 1800-000-000 if not done by you.",
            )
        assertEquals(SmsKind.FINANCE, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore < 0.2)
    }

    @Test
    fun brokerContractNoteIsFinance() {
        val verdict =
            classifier.classify(
                "VM-SYNTHBROKER-T",
                "Trade executed: BUY 10 units at Rs 250.00. Contract note sent. Demat a/c charges Rs 20 debited.",
            )
        assertEquals(SmsKind.FINANCE, verdict.kind)
    }

    @Test
    fun bankPromotionalCreditCardOfferIsPromotional() {
        val verdict =
            classifier.classify(
                "VM-SYNTHBANK-P",
                "Pre-approved credit card offer for valued customers. Limited period offer. " +
                    "Apply at synthcard.in/offer",
            )
        assertEquals(SmsKind.PROMOTIONAL, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore >= 0.5)
    }

    @Test
    fun billReminderIsTransactional() {
        val verdict =
            classifier.classify(
                "VM-SYNTHU-T",
                "Your electricity bill of Rs 1,200 is due on 10-11-26. Pay on time.",
            )
        assertEquals(SmsKind.TRANSACTIONAL, verdict.kind)
    }

    @Test
    fun uidaiGovernmentHeaderIsGovernment() {
        val verdict =
            classifier.classify(
                "BZ-UIDAI-G",
                "Your Aadhaar was used for authentication on 05-11-26. Contact helpline if this was not you.",
            )
        assertEquals(SmsKind.GOVERNMENT, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore < 0.2)
    }

    @Test
    fun courierOutForDeliveryIsDelivery() {
        val verdict =
            classifier.classify(
                "TM-SYNTHC",
                "Your parcel is out for delivery today. Delivery agent will call you. AWB 1234567890.",
            )
        assertEquals(SmsKind.DELIVERY, verdict.kind)
    }

    @Test
    fun dltPromotionalSaleIsPromotionalAboveHalf() {
        val verdict =
            classifier.classify(
                "TX-SYNTHS-P",
                "Mega sale! Flat 50% off on all items. Hurry, limited stock. Shop now at synthshop.in/deal",
            )
        assertEquals(SmsKind.PROMOTIONAL, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore >= 0.5)
    }

    @Test
    fun kycAndShortenerFromMobileIsScam() {
        val verdict =
            classifier.classify(
                "9000012345",
                "Dear customer, your KYC is incomplete. Your account will be blocked today. Update now: bit.ly/x7Q2a",
            )
        assertEquals(SmsKind.SCAM, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore >= 0.8)
    }

    @Test
    fun leetspeakLoanIsDetected() {
        val verdict = classifier.classify("8000012345", "Congrats! fr33 l0an approved instantly. Apply now")
        assertTrue(verdict.signals.any { it.name == "credit_offer" })
        assertTrue("score ${verdict.spamScore}", verdict.spamScore >= 0.6)
    }

    @Test
    fun personalMessageFromMobileIsPersonal() {
        val verdict = classifier.classify("9000012345", "are you coming home")
        assertEquals(SmsKind.PERSONAL, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore < 0.3)
    }

    @Test
    fun verdictListsAtMostFiveSignals() {
        val verdict =
            classifier.classify(
                "8000012345",
                "Congrats! fr33 l0an approved. Flat 50% off, hurry, limited offer. Click bit.ly/ab12 now. " +
                    "KYC pending, account blocked today. Update now.",
            )
        assertEquals(5, verdict.signals.size)
    }

    @Test
    fun learningMovesScoresTowardTheLearnedLabel() {
        val learner = SmsClassifier()
        val spamProbe = "blorpix frazzle zanthic"
        val hamProbe = "moonlit tea garden"
        val spamBefore = learner.classify(SENDER, spamProbe).spamScore
        val hamBefore = learner.classify(SENDER, hamProbe).spamScore
        repeat(25) { i ->
            learner.learn(SENDER, "blorpix frazzle zanthic notice $i", spam = true)
            learner.learn(SENDER, "moonlit tea garden walk $i", spam = false)
        }
        assertTrue(learner.classify(SENDER, spamProbe).spamScore > spamBefore)
        assertTrue(learner.classify(SENDER, hamProbe).spamScore < hamBefore)
    }

    @Test
    fun tokenModelStaysSilentBelowMinimumDocs() {
        val learner = SmsClassifier()
        val before = learner.classify(SENDER, "blorpix frazzle zanthic").spamScore
        repeat(19) { i -> learner.learn(SENDER, "blorpix frazzle zanthic notice $i", spam = true) }
        val after = learner.classify(SENDER, "blorpix frazzle zanthic").spamScore
        assertEquals(before, after, 0.0)
    }

    @Test
    fun classifiesOneThousandMessagesWithinBudget() {
        val trained = SmsClassifier()
        repeat(30) { i ->
            trained.learn("AD-SYNTHX-S", "Your order $i has shipped and will arrive soon", spam = false)
            trained.learn("9000012345", "Win a prize now, click the link $i", spam = true)
        }
        val messages =
            listOf(
                "BZ-SYNTHX-T" to "Your one time password is 482913. Do not share it with anyone.",
                "VM-SYNTHBANK-T" to "Rs 1,250.00 debited from A/c XX1234 on 05-11-26. Avl bal Rs 9,876.50.",
                "TX-SYNTHS-P" to "Mega sale! Flat 50% off on all items. Hurry, limited stock.",
                "9000012345" to "Dear customer, your KYC is incomplete. Update now: bit.ly/x7Q2a",
                "9000012345" to "are you coming home",
            )
        for (i in 0 until 200) {
            val (sender, body) = messages[i % messages.size]
            trained.classify(sender, body)
        }
        val start = System.nanoTime()
        for (i in 0 until 1000) {
            val (sender, body) = messages[i % messages.size]
            trained.classify(sender, body)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("1000 messages took $elapsedMs ms", elapsedMs < 200)
    }

    @Test
    fun dltServiceMessageWithoutOtherKindIsTransactional() {
        val verdict =
            classifier.classify(
                "VM-SYNTHSVC-S",
                "Your service request has been logged. We will update you soon.",
            )
        assertEquals(SmsKind.TRANSACTIONAL, verdict.kind)
    }

    @Test
    fun dltPromotionalMessageWithoutOtherKindIsPromotional() {
        val verdict =
            classifier.classify(
                "TX-SYNTHS-P",
                "Your request has been logged. We will update you soon.",
            )
        assertEquals(SmsKind.PROMOTIONAL, verdict.kind)
    }

    @Test
    fun shortCodeMessageWithoutOtherKindIsTransactional() {
        val verdict = classifier.classify("56767", "Your request has been logged.")
        assertEquals(SmsKind.TRANSACTIONAL, verdict.kind)
    }

    @Test
    fun otherSenderMessageWithoutOtherKindIsPersonal() {
        val verdict = classifier.classify("SYNTHMSG", "are you coming home")
        assertEquals(SmsKind.PERSONAL, verdict.kind)
    }

    @Test
    fun dltShortenerIsMildAndDoesNotMakeScam() {
        val verdict =
            classifier.classify(
                "AD-SYNTHPAY-P",
                "Synthpay IPO is here! Apply on the Synthpay app now. bit.ly/x7Q2a",
            )
        assertEquals(SmsKind.PROMOTIONAL, verdict.kind)
        assertTrue(verdict.signals.any { it.name == "url_shortener_dlt" })
    }

    @Test
    fun dltKycWithShortenerIsStillScam() {
        val verdict =
            classifier.classify(
                "AD-SYNTHPAY-S",
                "Your KYC is incomplete. Your account will be blocked today. Update now: bit.ly/x7Q2a",
            )
        assertEquals(SmsKind.SCAM, verdict.kind)
    }

    @Test
    fun negatedKycSafetyNoticeIsTransactional() {
        val verdict =
            classifier.classify(
                "VM-SYNTHPAY-S",
                "Synthpay will never call you for the card number, CVV or expiry date. " +
                    "Never share your KYC or OTP with anyone.",
            )
        assertEquals(SmsKind.TRANSACTIONAL, verdict.kind)
        assertTrue("score ${verdict.spamScore}", verdict.spamScore < 0.3)
        assertTrue(verdict.signals.none { it.name == "kyc_or_blocked_account" })
    }

    @Test
    fun kycWithoutNegationStillFires() {
        val verdict = classifier.classify("9000012345", "Your KYC is pending. Update now.")
        assertTrue(verdict.signals.any { it.name == "kyc_or_blocked_account" })
    }

    @Test
    fun negationInAnEarlierSentenceDoesNotHideKyc() {
        val verdict = classifier.classify("9000012345", "Never share your OTP. Your KYC is pending, update now.")
        assertTrue(verdict.signals.any { it.name == "kyc_or_blocked_account" })
    }

    private companion object {
        const val SENDER = "AD-SYNTHX-S"
    }
}
