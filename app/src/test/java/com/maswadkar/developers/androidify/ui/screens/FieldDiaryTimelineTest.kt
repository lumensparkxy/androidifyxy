package com.maswadkar.developers.androidify.ui.screens

import com.google.firebase.Timestamp
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.data.FieldDiaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FieldDiaryTimelineTest {

    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today: LocalDate = LocalDate.of(2026, 6, 23)

    @Test
    fun `timeline groups entries newest first by date`() {
        val entries = listOf(
            entry("old", DiaryActivityType.Harvest, LocalDateTime.of(2026, 6, 21, 11, 0)),
            entry("latest", DiaryActivityType.Irrigation, LocalDateTime.of(2026, 6, 23, 10, 15)),
            entry("yesterday", DiaryActivityType.Fertilizer, LocalDateTime.of(2026, 6, 22, 8, 30)),
            entry("early", DiaryActivityType.Pesticide, LocalDateTime.of(2026, 6, 23, 7, 30))
        )

        val groups = buildFieldDiaryTimelineGroups(
            entries = entries,
            selectedFilter = null,
            today = today,
            zoneId = zoneId
        )

        assertEquals(listOf("Today, 23 Jun", "Yesterday, 22 Jun", "21 Jun"), groups.map { it.label })
        assertEquals(listOf("latest", "early"), groups.first().entries.map { it.id })
    }

    @Test
    fun `timeline filter keeps matching activity without refetch concerns`() {
        val entries = listOf(
            entry("irrigation", DiaryActivityType.Irrigation, LocalDateTime.of(2026, 6, 23, 7, 30)),
            entry("fertilizer", DiaryActivityType.Fertilizer, LocalDateTime.of(2026, 6, 23, 8, 30)),
            entry("weeding", DiaryActivityType.Weeding, LocalDateTime.of(2026, 6, 23, 9, 30))
        )

        val filtered = filterFieldDiaryEntries(entries, DiaryActivityType.Weeding)

        assertEquals(listOf("weeding"), filtered.map { it.id })
    }

    @Test
    fun `detail parts omit blank optional fields`() {
        val details = buildFieldDiaryDetailParts(
            FieldDiaryEntry(
                cropName = " Soybean ",
                fieldName = " ",
                inputName = "Urea",
                quantity = null
            )
        )

        assertEquals(listOf("Soybean", "Urea"), details)
    }

    @Test
    fun `cost formatter handles rupee values cleanly`() {
        assertEquals("Rs 420", formatFieldDiaryCostAmount(420.0))
        assertEquals("Rs 3,850.5", formatFieldDiaryCostAmount(3850.5))
        assertEquals(null, formatFieldDiaryCostAmount(null))
        assertEquals(null, formatFieldDiaryCostAmount(-1.0))
    }

    @Test
    fun `time formatter returns local time label`() {
        val timestamp = timestamp(LocalDateTime.of(2026, 6, 23, 7, 30))

        assertEquals("07:30 AM", formatFieldDiaryTimeLabel(timestamp, zoneId))
    }

    @Test
    fun `empty filtered result produces no groups`() {
        val groups = buildFieldDiaryTimelineGroups(
            entries = listOf(entry("harvest", DiaryActivityType.Harvest, LocalDateTime.of(2026, 6, 23, 9, 0))),
            selectedFilter = DiaryActivityType.Pesticide,
            today = today,
            zoneId = zoneId
        )

        assertTrue(groups.isEmpty())
    }

    private fun entry(
        id: String,
        activityType: DiaryActivityType,
        activityDate: LocalDateTime
    ): FieldDiaryEntry = FieldDiaryEntry(
        id = id,
        activityType = activityType.firestoreValue,
        activityDate = timestamp(activityDate),
        cropName = "Soybean",
        notes = "Logged work"
    )

    private fun timestamp(dateTime: LocalDateTime): Timestamp {
        val instant = dateTime.atZone(zoneId).toInstant()
        return Timestamp(instant.epochSecond, instant.nano)
    }
}
