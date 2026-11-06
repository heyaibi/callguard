package com.callguard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialMatchTest {

    @Test
    fun letterMapsToItsKeypadDigit() {
        assertEquals('2', t9Digit('A'))
        assertEquals('2', t9Digit('c'))
        assertEquals('7', t9Digit('S'))
        assertEquals('9', t9Digit('z'))
        assertNull(t9Digit('1'))
    }

    @Test
    fun t9CodeSpellsTheLettersOfAWord() {
        assertEquals("727537", t9Code("Parker"))
        assertEquals("", t9Code("123"))
    }

    @Test
    fun t9MatchesFromTheStartOfAnyWord() {
        assertTrue(t9Matches("727", "Amelia Parker"))
        assertTrue(t9Matches("2", "Bella Gupta"))
        assertFalse(t9Matches("999", "Amelia Parker"))
        assertFalse(t9Matches("", "Amelia Parker"))
    }

    @Test
    fun dialedDigitsMatchTheNationalNumber() {
        assertTrue(dialMatches("98765", null, "+919876543210"))
        assertTrue(dialMatches("919876", null, "+919876543210"))
        assertFalse(dialMatches("98766", null, "+919876543210"))
    }

    @Test
    fun dialedDigitsMatchAContactByName() {
        assertTrue(dialMatches("2", "Bella Gupta", "+919812345678"))
        assertFalse(dialMatches("2", null, "+919812345678"))
    }

    @Test
    fun nothingDialedMatchesNothing() {
        assertFalse(dialMatches("", "Amelia Parker", "+919876543210"))
    }
}
