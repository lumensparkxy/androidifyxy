package com.maswadkar.developers.androidify.ui.screens

import com.google.firebase.Timestamp
import com.maswadkar.developers.androidify.data.DiaryActivityType
import com.maswadkar.developers.androidify.data.FieldDiaryEntry
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

data class FieldDiaryTimelineGroup(
    val label: String,
    val entries: List<FieldDiaryEntry>
)

fun buildFieldDiaryTimelineGroups(
    entries: List<FieldDiaryEntry>,
    selectedFilter: DiaryActivityType?,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<FieldDiaryTimelineGroup> {
    val visibleEntries = filterFieldDiaryEntries(entries, selectedFilter)
        .sortedByNewestActivityDate()

    return visibleEntries
        .groupBy { entry ->
            formatFieldDiaryDateLabel(
                date = entry.activityDate?.toLocalDate(zoneId),
                today = today
            )
        }
        .map { (label, groupEntries) ->
            FieldDiaryTimelineGroup(label = label, entries = groupEntries)
        }
}

fun filterFieldDiaryEntries(
    entries: List<FieldDiaryEntry>,
    selectedFilter: DiaryActivityType?
): List<FieldDiaryEntry> {
    if (selectedFilter == null) {
        return entries
    }

    return entries.filter { entry ->
        DiaryActivityType.fromFirestoreValue(entry.activityType) == selectedFilter
    }
}

fun buildFieldDiaryDetailParts(entry: FieldDiaryEntry): List<String> = buildList {
    entry.cropName.trim().takeIf { it.isNotBlank() }?.let(::add)
    entry.fieldName?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    entry.inputName?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    entry.quantity?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
}

fun formatFieldDiaryDateLabel(
    date: LocalDate?,
    today: LocalDate = LocalDate.now()
): String {
    if (date == null) {
        return "No date"
    }

    val dayMonth = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))
    return when (date) {
        today -> "Today, $dayMonth"
        today.minusDays(1) -> "Yesterday, $dayMonth"
        else -> dayMonth
    }
}

fun formatFieldDiaryTimeLabel(
    timestamp: Timestamp?,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    if (timestamp == null) {
        return "--"
    }

    return timestamp.toInstant()
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
}

fun formatFieldDiaryCostAmount(costAmount: Double?): String? {
    val amount = costAmount?.takeIf { it >= 0.0 } ?: return null
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    }

    return "Rs ${formatter.format(amount.roundToTwoDecimals())}"
}

private fun List<FieldDiaryEntry>.sortedByNewestActivityDate(): List<FieldDiaryEntry> =
    sortedWith(
        compareByDescending<FieldDiaryEntry> { it.activityDate?.seconds ?: Long.MIN_VALUE }
            .thenByDescending { it.activityDate?.nanoseconds ?: Int.MIN_VALUE }
    )

private fun Timestamp.toLocalDate(zoneId: ZoneId): LocalDate =
    toInstant().atZone(zoneId).toLocalDate()

private fun Double.roundToTwoDecimals(): Double =
    (this * 100.0).roundToLong() / 100.0
