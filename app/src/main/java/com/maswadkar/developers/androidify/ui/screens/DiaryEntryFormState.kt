package com.maswadkar.developers.androidify.ui.screens

import com.google.firebase.Timestamp
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.data.FieldDiaryEntry
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

const val FIELD_DIARY_MAX_PHOTOS = 5
const val FIELD_DIARY_NOTES_MAX_LENGTH = 200

enum class DiaryEntryFormMode {
    Add,
    Edit
}

enum class DiaryEntryFormFieldError {
    MissingActivityDate,
    MissingCropName,
    MissingNotes,
    InvalidCostAmount,
    NegativeCostAmount,
    TooManyPhotos
}

data class DiaryEntryFormValues(
    val activityDateMillis: Long? = null,
    val activityType: DiaryActivityType = DiaryActivityType.Irrigation,
    val cropName: String = "",
    val fieldName: String = "",
    val notes: String = "",
    val inputName: String = "",
    val quantity: String = "",
    val costAmountText: String = ""
)

data class NormalizedDiaryEntryFormValues(
    val activityDate: Timestamp,
    val activityType: DiaryActivityType,
    val cropName: String,
    val fieldName: String?,
    val notes: String,
    val inputName: String?,
    val quantity: String?,
    val costAmount: Double?
)

data class DiaryEntryFormValidationResult(
    val normalizedValues: NormalizedDiaryEntryFormValues?,
    val errors: Set<DiaryEntryFormFieldError>
)

fun validateDiaryEntryForm(
    values: DiaryEntryFormValues,
    photoCount: Int
): DiaryEntryFormValidationResult {
    val errors = linkedSetOf<DiaryEntryFormFieldError>()
    val activityDate = values.activityDateMillis
    if (activityDate == null) {
        errors += DiaryEntryFormFieldError.MissingActivityDate
    }

    val cropName = values.cropName.trim()
    if (cropName.isBlank()) {
        errors += DiaryEntryFormFieldError.MissingCropName
    }

    val notes = values.notes.trim()
    if (notes.isBlank()) {
        errors += DiaryEntryFormFieldError.MissingNotes
    }

    val costParseResult = parseDiaryEntryFormCostAmount(values.costAmountText)
    val costAmount = when (costParseResult) {
        DiaryCostAmountParseResult.Blank -> null
        DiaryCostAmountParseResult.Invalid -> {
            errors += DiaryEntryFormFieldError.InvalidCostAmount
            null
        }
        DiaryCostAmountParseResult.Negative -> {
            errors += DiaryEntryFormFieldError.NegativeCostAmount
            null
        }
        is DiaryCostAmountParseResult.Valid -> {
            costParseResult.amount
        }
    }

    if (photoCount > FIELD_DIARY_MAX_PHOTOS) {
        errors += DiaryEntryFormFieldError.TooManyPhotos
    }

    val normalized = if (errors.isEmpty() && activityDate != null) {
        NormalizedDiaryEntryFormValues(
            activityDate = Timestamp(Date(activityDate)),
            activityType = values.activityType,
            cropName = cropName,
            fieldName = values.fieldName.trimToNullable(),
            notes = notes,
            inputName = values.inputName.trimToNullable(),
            quantity = values.quantity.trimToNullable(),
            costAmount = costAmount
        )
    } else {
        null
    }

    return DiaryEntryFormValidationResult(
        normalizedValues = normalized,
        errors = errors
    )
}

fun buildDiaryEntryFromFormValues(
    entryId: String,
    existingCreatedAt: Timestamp?,
    values: NormalizedDiaryEntryFormValues,
    photoPaths: List<String>
): FieldDiaryEntry = FieldDiaryEntry(
    id = entryId,
    activityDate = values.activityDate,
    activityType = values.activityType.firestoreValue,
    cropName = values.cropName,
    fieldName = values.fieldName,
    notes = values.notes,
    inputName = values.inputName,
    quantity = values.quantity,
    costAmount = values.costAmount,
    photoPaths = photoPaths,
    createdAt = existingCreatedAt
)

fun DiaryEntryFormValues.withBoundedNotes(value: String): DiaryEntryFormValues =
    copy(notes = value.take(FIELD_DIARY_NOTES_MAX_LENGTH))

fun DiaryEntryFormValues.withEntry(entry: FieldDiaryEntry, fallbackMillis: Long): DiaryEntryFormValues = copy(
    activityDateMillis = entry.activityDate?.toDate()?.time ?: fallbackMillis,
    activityType = DiaryActivityType.fromFirestoreValue(entry.activityType),
    cropName = entry.cropName,
    fieldName = entry.fieldName.orEmpty(),
    notes = entry.notes.take(FIELD_DIARY_NOTES_MAX_LENGTH),
    inputName = entry.inputName.orEmpty(),
    quantity = entry.quantity.orEmpty(),
    costAmountText = formatDiaryEntryFormEditableCost(entry.costAmount)
)

fun formatDiaryEntryFormDate(
    millis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    if (millis == null) {
        return "Select date"
    }

    val date = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return formatFieldDiaryDateLabel(date = date, today = today)
}

fun formatDiaryEntryFormEditableCost(costAmount: Double?): String {
    val amount = costAmount ?: return ""
    return if (amount % 1.0 == 0.0) {
        amount.roundToLong().toString()
    } else {
        NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
            isGroupingUsed = false
        }.format(amount.roundToTwoDecimals())
    }
}

fun dateMillisFromLocalDate(
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

fun todayDiaryEntryFormDateMillis(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return dateMillisFromLocalDate(today, zoneId)
}

sealed interface DiaryCostAmountParseResult {
    data object Blank : DiaryCostAmountParseResult
    data object Invalid : DiaryCostAmountParseResult
    data object Negative : DiaryCostAmountParseResult
    data class Valid(val amount: Double) : DiaryCostAmountParseResult
}

fun parseDiaryEntryFormCostAmount(value: String): DiaryCostAmountParseResult {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return DiaryCostAmountParseResult.Blank
    }

    val normalized = trimmed.removePrefix("Rs").removePrefix("rs").removePrefix("₹").trim()
    val amount = normalized.replace(",", "").toDoubleOrNull()
        ?: return DiaryCostAmountParseResult.Invalid

    return if (amount < 0.0) {
        DiaryCostAmountParseResult.Negative
    } else {
        DiaryCostAmountParseResult.Valid(amount.roundToTwoDecimals())
    }
}

private fun String.trimToNullable(): String? =
    trim().takeIf { it.isNotBlank() }

private fun Double.roundToTwoDecimals(): Double =
    (this * 100.0).roundToLong() / 100.0
