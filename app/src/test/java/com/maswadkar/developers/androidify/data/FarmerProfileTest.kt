package com.maswadkar.developers.androidify.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmerProfileTest {

    @Test
    fun `lead required fields are detected correctly`() {
        val incomplete = FarmerProfile(
            district = "Pune",
            totalFarmAcres = 4.0
        )

        assertFalse(incomplete.hasLeadRequiredFields())
        assertEquals(listOf("name", "village", "tehsil", "mobileNumber"), incomplete.getMissingLeadFields())
    }

    @Test
    fun `normalized profile trims lead-critical fields`() {
        val normalized = FarmerProfile(
            name = "  Ramesh Patil  ",
            district = "  Pune ",
            village = "  Baramati  ",
            tehsil = "  Daund ",
            totalFarmAcres = 5.5,
            mobileNumber = "  98765 43210  "
        ).normalized()

        assertEquals("Ramesh Patil", normalized.name)
        assertEquals("Pune", normalized.district)
        assertEquals("Baramati", normalized.village)
        assertEquals("Daund", normalized.tehsil)
        assertEquals("9876543210", normalized.mobileNumber)
        assertTrue(normalized.hasLeadRequiredFields())
    }

    @Test
    fun `lead contact fallbacks normalize auth phone number`() {
        val normalized = FarmerProfile(
            name = "Ramesh Patil",
            district = "Pune",
            village = "Baramati",
            tehsil = "Daund",
            totalFarmAcres = 5.5
        ).withLeadContactFallbacks("+91 98765 43210")

        assertEquals("9876543210", normalized.mobileNumber)
        assertTrue(normalized.hasLeadRequiredFields())
    }

    @Test
    fun `mandi preferences projection keeps compact location fields`() {
        val profile = FarmerProfile(
            state = " Maharashtra ",
            district = " Pune ",
            market = " Baramati ",
            lastCommodity = " Onion "
        ).normalized()

        val preferences = profile.toMandiPreferences()

        assertEquals("Maharashtra", preferences.state)
        assertEquals("Pune", preferences.district)
        assertEquals("Baramati", preferences.market)
        assertEquals("Onion", preferences.lastCommodity)
        assertTrue(preferences.isValid())
    }
}

