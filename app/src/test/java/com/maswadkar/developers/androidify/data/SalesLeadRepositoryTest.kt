package com.maswadkar.developers.androidify.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SalesLeadRepositoryTest {

    @Test
    fun `doc id is stable across product casing and spacing`() {
        val first = buildSalesLeadDocId(
            userId = "user-123",
            conversationId = "conversation-456",
            productName = "  Boron   500 G  "
        )
        val second = buildSalesLeadDocId(
            userId = "user-123",
            conversationId = "conversation-456",
            productName = "boron 500 g"
        )

        assertEquals(first, second)
        assertEquals(32, first.length)
    }

    @Test
    fun `request number matches expected format`() {
        val requestNumber = generateSalesLeadRequestNumber(LocalDate.of(2026, 3, 10))

        assertTrue(requestNumber.matches(Regex("KR-20260310-[0-9A-F]{6}")))
    }
}

