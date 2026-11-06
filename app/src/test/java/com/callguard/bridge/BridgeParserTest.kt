package com.callguard.bridge

import com.callguard.core.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeParserTest {

    @Test
    fun incomingVoiceCallLineIsACall() {
        assertTrue(BridgeParser.isCall(null, listOf("Incoming voice call")))
        assertFalse(BridgeParser.isVideoCall(listOf("Incoming voice call")))
    }

    @Test
    fun incomingVideoCallLineIsAVideoCall() {
        assertTrue(BridgeParser.isCall(null, listOf("Incoming video call")))
        assertTrue(BridgeParser.isVideoCall(listOf("Incoming video call")))
    }

    @Test
    fun callCategoryIsACallEvenWithoutText() {
        assertTrue(BridgeParser.isCall(BridgeParser.CATEGORY_CALL, listOf(null, "")))
    }

    @Test
    fun callStyleActionsMarkACall() {
        assertTrue(BridgeParser.isCall(null, listOf("+91 98200 11223"), listOf("Decline", "Answer")))
    }

    @Test
    fun ordinaryMessageIsNotACall() {
        assertFalse(BridgeParser.isCall(null, listOf("+91 98200 11223", "Are you free for lunch?"), listOf("Reply", "Mark as read")))
    }

    @Test
    fun missedCallIsNotAnIncomingCall() {
        assertFalse(BridgeParser.isCall(null, listOf("Missed voice call")))
    }

    @Test
    fun numberTitleIsNormalizedToE164() {
        assertEquals(PhoneNumber("+919820011223"), BridgeParser.numberIn("+91 98200 11223"))
        assertEquals(PhoneNumber("+919820011223"), BridgeParser.numberIn("98200 11223"))
    }

    @Test
    fun savedContactNameHasNoNumber() {
        assertNull(BridgeParser.numberIn("Priya Sharma"))
        assertNull(BridgeParser.numberIn("Incoming voice call"))
        assertNull(BridgeParser.numberIn(null))
    }

    @Test
    fun numberIsFoundInsideTheTildeTitle() {
        assertEquals(PhoneNumber("+919820011223"), BridgeParser.numberIn("+91 98200 11223 ~ Ramesh"))
    }

    @Test
    fun tildeProfileNameIsExtracted() {
        assertEquals("Ramesh", BridgeParser.profileName("~ Ramesh: Are you free?"))
        assertEquals("Ramesh", BridgeParser.profileName("+91 98200 11223 ~Ramesh"))
        assertEquals("Ramesh", BridgeParser.profileName("~ Ramesh · Incoming voice call"))
    }

    @Test
    fun devanagariProfileNameIsKept() {
        assertEquals("सुनिता", BridgeParser.profileName("~ सुनिता: नमस्ते"))
    }

    @Test
    fun tildeInsideAMessageIsNotAProfileName() {
        assertNull(BridgeParser.profileName("Hi ~ there"))
        assertNull(BridgeParser.profileName("~ 98200 11223"))
        assertNull(BridgeParser.profileName("Priya Sharma"))
        assertNull(BridgeParser.profileName(null))
    }
}
