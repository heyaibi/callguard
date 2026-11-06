package com.callguard.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagesEmptyLabelTest {
    @Test
    fun nothingIsShownBeforeTheFirstLoadFinishes() {
        assertNull(emptyListLabel(loaded = false, visibleRows = 0, query = "", quiet = false))
        assertNull(emptyListLabel(loaded = false, visibleRows = 0, query = "ann", quiet = true))
    }

    @Test
    fun nothingIsShownWhenRowsAreVisible() {
        assertNull(emptyListLabel(loaded = true, visibleRows = 3, query = "", quiet = false))
    }

    @Test
    fun aSearchWithNoHitsSaysSo() {
        assertEquals("NO MATCHES", emptyListLabel(loaded = true, visibleRows = 0, query = "zzz", quiet = false))
    }

    @Test
    fun aBlankSearchCountsAsNoSearch() {
        assertEquals("NO TEXTS YET", emptyListLabel(loaded = true, visibleRows = 0, query = "   ", quiet = false))
    }

    @Test
    fun theQuietInboxAndTheInboxSayTheirOwnWords() {
        assertEquals("QUIET INBOX IS EMPTY", emptyListLabel(loaded = true, visibleRows = 0, query = "", quiet = true))
        assertEquals("NO TEXTS YET", emptyListLabel(loaded = true, visibleRows = 0, query = "", quiet = false))
    }
}
