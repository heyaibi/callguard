package com.callguard.messaging

import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.spam.classify.SenderKind
import com.callguard.spam.classify.SenderParser

/**
 * The number an SMS address stands for. Numbers normalize to E.164. Addresses that do not normalize
 * (7-digit local numbers, odd formats) keep their text, so the sender is still tracked and filtered.
 */
fun addressToNumber(address: String): PhoneNumber {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) return PhoneNumber("UNKNOWN")
    return PhoneNumbers.normalize(trimmed) ?: PhoneNumber(trimmed)
}

/** True when a stored provider address is the same sender, whatever format the provider kept. */
fun sameAddress(
    address: String,
    number: PhoneNumber,
): Boolean = addressToNumber(address) == number

/**
 * The key that a per-sender ring or mute setting is stored under. A DLT sender uses its brand header in upper case,
 * so "VM-HDFCBK-S" and "AX-HDFCBK" share the key "HDFCBK". Any other sender uses the e164 form of its address.
 */
fun senderKey(address: String): String {
    val info = SenderParser.parse(address)
    return if (info.kind == SenderKind.DLT) info.name else addressToNumber(address).e164.uppercase()
}
