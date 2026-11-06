package com.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    private fun e164(raw: String): String? = PhoneNumbers.normalize(raw)?.e164

    @Test
    fun internationalFormKeepsPlusAndDigits() {
        assertEquals("+919876543210", e164("+919876543210"))
    }

    @Test
    fun spacesDashesAndParensAreStripped() {
        assertEquals("+919876543210", e164("+91 98765 43210"))
        assertEquals("+919876543210", e164("+91-98765-43210"))
        assertEquals("+919876543210", e164("(+91) 98765 43210"))
        assertEquals("+919876543210", e164("98765-43210"))
    }

    @Test
    fun zeroZeroPrefixBecomesPlus() {
        assertEquals("+919876543210", e164("00919876543210"))
        assertEquals("+919876543210", e164("00 91 98765 43210"))
    }

    @Test
    fun tenDigitIndianMobileGetsCountryCode() {
        assertEquals("+919876543210", e164("9876543210"))
        assertEquals("+916123456789", e164("6123456789"))
    }

    @Test
    fun leadingTrunkZeroWithTenDigitsGetsCountryCode() {
        assertEquals("+919876543210", e164("09876543210"))
        assertEquals("+918023456789", e164("080-2345-6789"))
    }

    @Test
    fun tollFreeElevenDigitsStartingWith1800Or1860GetCountryCode() {
        assertEquals("+9118001234567", e164("18001234567"))
        assertEquals("+9118001234567", e164("1800 123 4567"))
        assertEquals("+9118601234567", e164("18601234567"))
    }

    @Test
    fun otherElevenDigitsStartingWithOneAreNotNormalized() {
        assertNull(e164("11234567890"))
    }

    @Test
    fun ninetyOneWithTenDigitsGetsPlusOnly() {
        assertEquals("+919876543210", e164("919876543210"))
    }

    @Test
    fun tenDigitNumberNotStartingWithMobilePrefixIsNotNormalized() {
        assertNull(e164("5876543210"))
    }

    @Test
    fun alphanumericSenderIsUppercasedAndTrimmed() {
        assertEquals("VM-HDFCBK", PhoneNumbers.normalize("VM-HDFCBK")?.e164)
        assertEquals("VM-HDFCBK", PhoneNumbers.normalize("  vm-hdfcbk ")?.e164)
    }

    @Test
    fun shortCodeKeepsDigits() {
        assertEquals("56767", e164("56767"))
        assertEquals("121", e164("121"))
    }

    @Test
    fun blankInputIsNull() {
        assertNull(e164(""))
        assertNull(e164("   "))
    }

    @Test
    fun garbageIsNull() {
        assertNull(e164("*123#"))
        assertNull(e164("+"))
        assertNull(e164("12"))
        assertNull(e164("+1234"))
        assertNull(e164("+1234567890123456"))
        assertNull(e164("98765+43210"))
    }

    @Test
    fun nonIndianRegionAcceptsOnlyInternationalForms() {
        assertNull(PhoneNumbers.normalize("9876543210", "US"))
        assertEquals("+14155552671", PhoneNumbers.normalize("+1 415 555 2671", "US")?.e164)
        assertEquals("+14155552671", PhoneNumbers.normalize("0014155552671", "US")?.e164)
    }

    @Test
    fun sameNumberInDifferentFormsIsEqual() {
        assertEquals(PhoneNumbers.normalize("9876543210"), PhoneNumbers.normalize("+91 98765 43210"))
        assertEquals(PhoneNumbers.normalize("09876543210"), PhoneNumbers.normalize("00919876543210"))
    }

    @Test
    fun formatUsesGroupedStyleForIndianNumbers() {
        assertEquals("+91 98765 43210", PhoneNumbers.format(PhoneNumber("+919876543210")))
    }

    @Test
    fun formatReturnsE164ForOtherNumbers() {
        assertEquals("+14155552671", PhoneNumbers.format(PhoneNumber("+14155552671")))
        assertEquals("VM-HDFCBK", PhoneNumbers.format(PhoneNumber("VM-HDFCBK")))
    }
}
