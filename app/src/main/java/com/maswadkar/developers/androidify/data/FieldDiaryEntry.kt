package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp
import java.util.Locale

private const val FIELD_DIARY_STORAGE_ROOT = "field_diary"

enum class DiaryActivityType(val firestoreValue: String, val displayName: String) {
    Irrigation("irrigation", "Irrigation"),
    Fertilizer("fertilizer", "Fertilizer"),
    Pesticide("pesticide", "Pesticide"),
    Harvest("harvest", "Harvest"),
    Other("other", "Other");

    companion object {
        fun fromFirestoreValue(value: String?): DiaryActivityType =
            entries.firstOrNull { it.firestoreValue == value?.trim()?.lowercase(Locale.ROOT) } ?: Other

        fun isSupportedFirestoreValue(value: String?): Boolean =
            entries.any { it.firestoreValue == value?.trim()?.lowercase(Locale.ROOT) }
    }
}

enum class FieldDiaryValidationError(val message: String) {
    MissingActivityDate("Activity date is required"),
    InvalidActivityType("Activity type is not supported"),
    MissingCropName("Crop name is required"),
    MissingNotes("Notes are required"),
    NegativeCostAmount("Cost must be zero or greater"),
    InvalidPhotoPath("Photo paths must stay under the entry storage path")
}

data class FieldDiaryEntry(
    val id: String = "",
    val userId: String = "",
    val activityDate: Timestamp? = null,
    val activityType: String = DiaryActivityType.Other.firestoreValue,
    val cropName: String = "",
    val fieldName: String? = null,
    val notes: String = "",
    val inputName: String? = null,
    val quantity: String? = null,
    val costAmount: Double? = null,
    val photoPaths: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

fun FieldDiaryEntry.normalizedForSave(userId: String, entryId: String = id): FieldDiaryEntry = copy(
    id = entryId.trim(),
    userId = userId.trim(),
    activityType = DiaryActivityType.fromFirestoreValue(activityType).firestoreValue,
    cropName = cropName.trim(),
    fieldName = normalizeOptionalDiaryText(fieldName),
    notes = notes.trim(),
    inputName = normalizeOptionalDiaryText(inputName),
    quantity = normalizeOptionalDiaryText(quantity),
    photoPaths = photoPaths.map { it.trim() }.filter { it.isNotBlank() }
)

fun FieldDiaryEntry.validationErrorsForSave(userId: String, entryId: String = id): List<FieldDiaryValidationError> {
    val normalized = normalizedForSave(userId, entryId)

    return buildList {
        if (normalized.activityDate == null) {
            add(FieldDiaryValidationError.MissingActivityDate)
        }
        if (!DiaryActivityType.isSupportedFirestoreValue(activityType)) {
            add(FieldDiaryValidationError.InvalidActivityType)
        }
        if (normalized.cropName.isBlank()) {
            add(FieldDiaryValidationError.MissingCropName)
        }
        if (normalized.notes.isBlank()) {
            add(FieldDiaryValidationError.MissingNotes)
        }
        if (normalized.costAmount != null && normalized.costAmount < 0.0) {
            add(FieldDiaryValidationError.NegativeCostAmount)
        }
        if (normalized.photoPaths.any { !isFieldDiaryPhotoPathForEntry(it, normalized.userId, normalized.id) }) {
            add(FieldDiaryValidationError.InvalidPhotoPath)
        }
    }
}

fun parseFieldDiaryCostAmount(value: String?): Double? {
    val normalized = normalizeOptionalDiaryText(value) ?: return null
    return normalized.toDoubleOrNull()?.takeIf { it >= 0.0 }
}

fun buildFieldDiaryPhotoPathPrefix(userId: String, entryId: String): String =
    "$FIELD_DIARY_STORAGE_ROOT/${userId.trim()}/${entryId.trim()}/"

fun isFieldDiaryPhotoPathForEntry(path: String, userId: String, entryId: String): Boolean {
    val trimmedPath = path.trim()
    val prefix = buildFieldDiaryPhotoPathPrefix(userId, entryId)

    return userId.isNotBlank() &&
        entryId.isNotBlank() &&
        trimmedPath.startsWith(prefix) &&
        trimmedPath.endsWith(".jpg") &&
        trimmedPath.length > prefix.length
}

private fun normalizeOptionalDiaryText(value: String?): String? =
    value?.trim()?.takeIf { it.isNotBlank() }
