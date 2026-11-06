package com.callguard.messaging

import android.content.Intent
import android.provider.Telephony

/** The SMS parts an SMS_DELIVER or SMS_RECEIVED broadcast carries, or null when the intent has none. */
fun smsParts(intent: Intent): List<SmsPart>? =
    Telephony.Sms.Intents.getMessagesFromIntent(intent)?.map { sms ->
        SmsPart(
            address = sms.originatingAddress.orEmpty(),
            body = sms.messageBody.orEmpty(),
            timestamp = sms.timestampMillis,
        )
    }
