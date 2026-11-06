package com.callguard.spam.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class SenderParserTest {
    @Test
    fun parsesDltHeaderWithoutSuffix() {
        val info = SenderParser.parse("VM-SYNTHBK")
        assertEquals(SenderKind.DLT, info.kind)
        assertEquals("SYNTHBK", info.name)
        assertEquals(null, info.suffix)
    }

    @Test
    fun parsesDltSuffixAndUpperCasesIt() {
        assertEquals('G', SenderParser.parse("BZ-UIDAI-G").suffix)
        assertEquals('T', SenderParser.parse("ad-synthx-t").suffix)
    }

    @Test
    fun recognisesMobileNumberForms() {
        for (sender in listOf("+91 90000 12345", "919000012345", "9000012345", "09000012345")) {
            assertEquals(sender, SenderKind.MOBILE, SenderParser.parse(sender).kind)
        }
    }

    @Test
    fun recognisesShortCodes() {
        assertEquals(SenderKind.SHORT_CODE, SenderParser.parse("56767").kind)
    }

    @Test
    fun treatsOtherAddressesAsOther() {
        assertEquals(SenderKind.OTHER, SenderParser.parse("SYNTHBANK").kind)
        assertEquals(SenderKind.OTHER, SenderParser.parse("+1 555 123 4567").kind)
        assertEquals(SenderKind.OTHER, SenderParser.parse("Jio-Care").kind)
    }
}
