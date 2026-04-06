package com.maswadkar.developers.androidify.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallAttributionRepositoryTest {

    @Test
    fun `extracts promoter id from simple referrer`() {
        assertEquals("raj", parsePromoterIdFromRawReferrer("pid=raj"))
    }

    @Test
    fun `extracts promoter id from multi param referrer`() {
        assertEquals("sneha", parsePromoterIdFromRawReferrer("utm_source=play&pid=sneha&utm_medium=link"))
    }

    @Test
    fun `returns null when pid is missing or blank`() {
        assertNull(parsePromoterIdFromRawReferrer("utm_source=play"))
        assertNull(parsePromoterIdFromRawReferrer("pid="))
        assertNull(parsePromoterIdFromRawReferrer(null))
    }

    @Test
    fun `organic status is used when pid is absent`() {
        assertEquals("organic_or_unknown", getAttributionStatus(null))
    }

    @Test
    fun `promoter attributed status is used when pid exists`() {
        assertEquals("promoter_attributed", getAttributionStatus("raj"))
    }
}

