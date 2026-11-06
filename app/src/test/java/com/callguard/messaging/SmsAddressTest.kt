package com.callguard.messaging

import com.callguard.core.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAddressTest {

    @Test
    fun indianNumbersNormalizeToE164() {
        assertEquals(PhoneNumber("+919876543210"), addressToNumber("9876543210"))
        assertEquals(PhoneNumber("+919876543210"), addressToNumber("09876543210"))
        assertEquals(PhoneNumber("+919876543210"), addressToNumber("+91 98765 43210"))
    }

    @Test
    fun shortCodesAndUnnormalizableAddressesKeepTheirText() {
        assertEquals(PhoneNumber("56767"), addressToNumber("56767"))
        assertEquals(PhoneNumber("5554001"), addressToNumber("5554001"))
    }

    @Test
    fun blankAddressBecomesUnknown() {
        assertEquals(PhoneNumber("UNKNOWN"), addressToNumber("  "))
    }

    @Test
    fun alphanumericSendersMatchCaseInsensitively() {
        assertEquals(PhoneNumber("VM-HDFCBK"), addressToNumber("vm-hdfcbk"))
    }

    @Test
    fun sameAddressMatchesAcrossProviderFormats() {
        assertTrue(sameAddress("09876543210", PhoneNumber("+919876543210")))
        assertTrue(sameAddress("5554001", PhoneNumber("5554001")))
    }

    @Test
    fun sameAddressRejectsOtherNumbers() {
        assertFalse(sameAddress("+919876543211", PhoneNumber("+919876543210")))
        assertFalse(sameAddress("5554002", PhoneNumber("5554001")))
    }
}
