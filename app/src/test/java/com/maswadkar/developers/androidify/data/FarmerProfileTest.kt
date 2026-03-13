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
        assertEquals(listOf("name", "village", "tehsil"), incomplete.getMissingLeadFields())
    }

    @Test
    fun `normalized profile trims lead-critical fields`() {
        val normalized = FarmerProfile(
            name = "  Ramesh Patil  ",
            district = "  Pune ",
            village = "  Baramati  ",
            tehsil = "  Daund ",
            totalFarmAcres = 5.5
        ).normalized()

        assertEquals("Ramesh Patil", normalized.name)
        assertEquals("Pune", normalized.district)
        assertEquals("Baramati", normalized.village)
        assertEquals("Daund", normalized.tehsil)
        assertTrue(normalized.hasLeadRequiredFields())
    }
}

