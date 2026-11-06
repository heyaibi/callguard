package com.callguard.messaging

import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers

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
fun sameAddress(address: String, number: PhoneNumber): Boolean = addressToNumber(address) == number
