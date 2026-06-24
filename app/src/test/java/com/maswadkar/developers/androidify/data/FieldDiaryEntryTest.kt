package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldDiaryEntryTest {

    @Test
    fun `activity type parses stored values and falls back for display`() {
        assertEquals(DiaryActivityType.LandPreparation, DiaryActivityType.fromFirestoreValue(" land_preparation "))
        assertEquals(DiaryActivityType.Sowing, DiaryActivityType.fromFirestoreValue("SOWING"))
        assertEquals(DiaryActivityType.Transplanting, DiaryActivityType.fromFirestoreValue("transplanting"))
        assertEquals(DiaryActivityType.Irrigation, DiaryActivityType.fromFirestoreValue(" irrigation "))
        assertEquals(DiaryActivityType.Fertilizer, DiaryActivityType.fromFirestoreValue("FERTILIZER"))
        assertEquals(DiaryActivityType.Weeding, DiaryActivityType.fromFirestoreValue("weeding"))
        assertEquals(DiaryActivityType.Mulching, DiaryActivityType.fromFirestoreValue("mulching"))
        assertEquals(DiaryActivityType.PostHarvest, DiaryActivityType.fromFirestoreValue("post_harvest"))
        assertEquals(DiaryActivityType.Other, DiaryActivityType.fromFirestoreValue("unexpected"))
        assertTrue(DiaryActivityType.isSupportedFirestoreValue("harvest"))
        assertTrue(DiaryActivityType.isSupportedFirestoreValue("land_preparation"))
        assertFalse(DiaryActivityType.isSupportedFirestoreValue("spraying"))
    }

    @Test
    fun `normalization trims strings and nulls optional blanks`() {
        val entry = FieldDiaryEntry(
            id = " entry-1 ",
            userId = "ignored",
            activityDate = Timestamp(100, 0),
            activityType = " PESTICIDE ",
            cropName = "  Cotton ",
            fieldName = "   ",
            notes = "  Sprayed after scouting  ",
            inputName = "  Neem oil ",
            quantity = "",
            costAmount = 120.0,
            photoPaths = listOf(" field_diary/user-1/entry-1/a.jpg ", "")
        ).normalizedForSave(userId = "user-1", entryId = "entry-1")

        assertEquals("entry-1", entry.id)
        assertEquals("user-1", entry.userId)
        assertEquals(DiaryActivityType.Pesticide.firestoreValue, entry.activityType)
        assertEquals("Cotton", entry.cropName)
        assertNull(entry.fieldName)
        assertEquals("Sprayed after scouting", entry.notes)
        assertEquals("Neem oil", entry.inputName)
        assertNull(entry.quantity)
        assertEquals(listOf("field_diary/user-1/entry-1/a.jpg"), entry.photoPaths)
    }

    @Test
    fun `validation reports required fields negative cost and invalid photo path`() {
        val errors = FieldDiaryEntry(
            activityType = "spraying",
            cropName = " ",
            notes = "",
            costAmount = -1.0,
            photoPaths = listOf("field_diary/other-user/entry-1/a.jpg")
        ).validationErrorsForSave(userId = "user-1", entryId = "entry-1")

        assertEquals(
            listOf(
                FieldDiaryValidationError.MissingActivityDate,
                FieldDiaryValidationError.InvalidActivityType,
                FieldDiaryValidationError.MissingCropName,
                FieldDiaryValidationError.MissingNotes,
                FieldDiaryValidationError.NegativeCostAmount,
                FieldDiaryValidationError.InvalidPhotoPath
            ),
            errors
        )
    }

    @Test
    fun `valid entry accepts own storage paths`() {
        val errors = FieldDiaryEntry(
            activityDate = Timestamp(200, 0),
            activityType = DiaryActivityType.Harvest.firestoreValue,
            cropName = "Wheat",
            notes = "Harvested north plot",
            photoPaths = listOf("field_diary/user-1/entry-1/1700000000000_abcd1234.jpg")
        ).validationErrorsForSave(userId = "user-1", entryId = "entry-1")

        assertTrue(errors.isEmpty())
        assertTrue(
            isFieldDiaryPhotoPathForEntry(
                "field_diary/user-1/entry-1/1700000000000_abcd1234.jpg",
                "user-1",
                "entry-1"
            )
        )
    }

    @Test
    fun `cost parsing returns null for blank invalid or negative values`() {
        assertEquals(125.5, parseFieldDiaryCostAmount(" 125.50 ") ?: -1.0, 0.0)
        assertNull(parseFieldDiaryCostAmount(""))
        assertNull(parseFieldDiaryCostAmount("abc"))
        assertNull(parseFieldDiaryCostAmount("-1"))
    }
}
