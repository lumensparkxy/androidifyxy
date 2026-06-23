package com.maswadkar.developers.androidify.ui.screens

import com.google.firebase.Timestamp
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.data.FieldDiaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DiaryEntryFormStateTest {

    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata")
    private val dateMillis = dateMillisFromLocalDate(LocalDate.of(2026, 6, 23), zoneId)

    @Test
    fun `validation reports required fields`() {
        val result = validateDiaryEntryForm(
            values = DiaryEntryFormValues(
                activityDateMillis = null,
                cropName = " ",
                notes = ""
            ),
            photoCount = 0
        )

        assertEquals(
            setOf(
                DiaryEntryFormFieldError.MissingActivityDate,
                DiaryEntryFormFieldError.MissingCropName,
                DiaryEntryFormFieldError.MissingNotes
            ),
            result.errors
        )
        assertNull(result.normalizedValues)
    }

    @Test
    fun `validation normalizes optional fields and parses rupee cost`() {
        val result = validateDiaryEntryForm(
            values = DiaryEntryFormValues(
                activityDateMillis = dateMillis,
                activityType = DiaryActivityType.Fertilizer,
                cropName = " Soybean ",
                fieldName = " Field A ",
                notes = " Applied after irrigation ",
                inputName = " Urea ",
                quantity = " 1 bag ",
                costAmountText = "Rs 850.50"
            ),
            photoCount = FIELD_DIARY_MAX_PHOTOS
        )

        val normalized = requireNotNull(result.normalizedValues)
        assertTrue(result.errors.isEmpty())
        assertEquals(DiaryActivityType.Fertilizer, normalized.activityType)
        assertEquals("Soybean", normalized.cropName)
        assertEquals("Field A", normalized.fieldName)
        assertEquals("Applied after irrigation", normalized.notes)
        assertEquals("Urea", normalized.inputName)
        assertEquals("1 bag", normalized.quantity)
        assertEquals(850.5, normalized.costAmount ?: -1.0, 0.0)
    }

    @Test
    fun `validation distinguishes invalid negative and blank costs`() {
        assertEquals(
            setOf(DiaryEntryFormFieldError.InvalidCostAmount),
            validateDiaryEntryForm(validValues(costAmountText = "abc"), photoCount = 0).errors
        )
        assertEquals(
            setOf(DiaryEntryFormFieldError.NegativeCostAmount),
            validateDiaryEntryForm(validValues(costAmountText = "-1"), photoCount = 0).errors
        )
        assertTrue(validateDiaryEntryForm(validValues(costAmountText = " "), photoCount = 0).errors.isEmpty())
    }

    @Test
    fun `validation rejects more than five photos`() {
        val result = validateDiaryEntryForm(
            values = validValues(),
            photoCount = FIELD_DIARY_MAX_PHOTOS + 1
        )

        assertEquals(setOf(DiaryEntryFormFieldError.TooManyPhotos), result.errors)
        assertNull(result.normalizedValues)
    }

    @Test
    fun `entry builder preserves edit createdAt and photo paths`() {
        val normalized = requireNotNull(
            validateDiaryEntryForm(
                values = validValues(costAmountText = "1,200"),
                photoCount = 2
            ).normalizedValues
        )
        val createdAt = Timestamp(100, 0)

        val entry = buildDiaryEntryFromFormValues(
            entryId = "entry-1",
            existingCreatedAt = createdAt,
            values = normalized,
            photoPaths = listOf(
                "field_diary/user-1/entry-1/a.jpg",
                "field_diary/user-1/entry-1/b.jpg"
            )
        )

        assertEquals("entry-1", entry.id)
        assertEquals(createdAt, entry.createdAt)
        assertEquals(1200.0, entry.costAmount ?: -1.0, 0.0)
        assertEquals(
            listOf(
                "field_diary/user-1/entry-1/a.jpg",
                "field_diary/user-1/entry-1/b.jpg"
            ),
            entry.photoPaths
        )
    }

    @Test
    fun `existing entry populates form values for edit`() {
        val entry = FieldDiaryEntry(
            activityDate = Timestamp(dateMillis / 1000, 0),
            activityType = DiaryActivityType.Harvest.firestoreValue,
            cropName = "Cotton",
            fieldName = "North plot",
            notes = "Harvested",
            inputName = "Labor",
            quantity = "2 workers",
            costAmount = 950.0
        )

        val values = DiaryEntryFormValues().withEntry(entry, fallbackMillis = 0)

        assertEquals(dateMillis / 1000, values.activityDateMillis?.div(1000))
        assertEquals(DiaryActivityType.Harvest, values.activityType)
        assertEquals("Cotton", values.cropName)
        assertEquals("North plot", values.fieldName)
        assertEquals("Harvested", values.notes)
        assertEquals("Labor", values.inputName)
        assertEquals("2 workers", values.quantity)
        assertEquals("950", values.costAmountText)
    }

    @Test
    fun `notes are capped at the form limit`() {
        val longNotes = "x".repeat(FIELD_DIARY_NOTES_MAX_LENGTH + 20)

        val values = DiaryEntryFormValues().withBoundedNotes(longNotes)

        assertEquals(FIELD_DIARY_NOTES_MAX_LENGTH, values.notes.length)
    }

    private fun validValues(costAmountText: String = ""): DiaryEntryFormValues =
        DiaryEntryFormValues(
            activityDateMillis = dateMillis,
            activityType = DiaryActivityType.Irrigation,
            cropName = "Wheat",
            notes = "Irrigated west plot",
            costAmountText = costAmountText
        )
}
