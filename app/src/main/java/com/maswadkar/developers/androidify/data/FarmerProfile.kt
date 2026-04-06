package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp

internal fun sanitizeLeadMobileInput(value: String): String = value
    .filter(Char::isDigit)
    .takeLast(10)

internal fun normalizeLeadMobileNumber(value: String?): String? = sanitizeLeadMobileInput(value.orEmpty())
    .takeIf { it.length == 10 }

internal fun normalizeLeadEmail(value: String?): String? = value
    ?.trim()
    ?.takeIf { it.isNotBlank() }

internal fun hasValidLeadMobileNumber(value: String?): Boolean = normalizeLeadMobileNumber(value) != null

internal fun FarmerProfile.withLeadContactFallbacks(phoneNumber: String?, email: String? = null): FarmerProfile = copy(
    mobileNumber = normalizeLeadMobileNumber(mobileNumber) ?: normalizeLeadMobileNumber(phoneNumber),
    emailId = normalizeLeadEmail(emailId) ?: normalizeLeadEmail(email)
).normalized()

/**
 * Canonical farmer profile containing location preferences, farm details, and contact info.
 * This is the primary persisted user settings document, and mandi UI state derives a
 * compact projection from this model instead of owning a separate source of truth.
 */
data class FarmerProfile(
    // Identity
    val name: String? = null,

    // Location fields (mandi preferences now project from these)
    val state: String = "",
    val district: String = "",
    val village: String? = null,
    val tehsil: String? = null,
    val market: String? = null,
    val lastCommodity: String? = null,

    // GPS Location (from Weather feature - read-only display)
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationLabel: String? = null,

    // Farm details
    val totalFarmAcres: Double? = null,
    val irrigationAvailable: Boolean? = null,

    // Contact information
    val mobileNumber: String? = null,
    val emailId: String? = null,

    // Crops
    val majorCrops: List<String> = emptyList(),

    // Metadata
    val updatedAt: Timestamp? = null
) {
    // No-arg constructor for Firestore
    constructor() : this(null, "", "", null, null, null, null, null, null, null, null, null, null, null, emptyList(), null)

    /**
     * Check if the profile has valid location data (minimum requirement)
     */
    fun hasValidLocation(): Boolean = state.isNotBlank() && district.isNotBlank()

    /**
     * Check if the profile has the minimum required fields for creating a sales lead.
     */
    fun hasLeadRequiredFields(): Boolean =
        !name.isNullOrBlank() &&
            !village.isNullOrBlank() &&
            !tehsil.isNullOrBlank() &&
            district.isNotBlank() &&
            totalFarmAcres != null && totalFarmAcres > 0 &&
            hasValidLeadMobileNumber(mobileNumber)

    /**
     * Return the missing lead-required fields for UI prompts.
     */
    fun getMissingLeadFields(): List<String> = buildList {
        if (name.isNullOrBlank()) add("name")
        if (village.isNullOrBlank()) add("village")
        if (tehsil.isNullOrBlank()) add("tehsil")
        if (district.isBlank()) add("district")
        if (totalFarmAcres == null || totalFarmAcres <= 0) add("totalFarmAcres")
        if (!hasValidLeadMobileNumber(mobileNumber)) add("mobileNumber")
    }

    /**
     * Create a normalized copy suitable for snapshots and validation.
     */
    fun normalized(): FarmerProfile = copy(
        name = name?.trim()?.takeIf { it.isNotBlank() },
        state = state.trim(),
        district = district.trim(),
        village = village?.trim()?.takeIf { it.isNotBlank() },
        tehsil = tehsil?.trim()?.takeIf { it.isNotBlank() },
        market = market?.trim()?.takeIf { it.isNotBlank() },
        lastCommodity = lastCommodity?.trim()?.takeIf { it.isNotBlank() },
        mobileNumber = normalizeLeadMobileNumber(mobileNumber),
        emailId = normalizeLeadEmail(emailId)
    )

    /**
     * Build the compact mandi-preferences projection used by the mandi prices UI
        * state.
     */
    fun toMandiPreferences(): MandiPreferences = MandiPreferences(
        state = state,
        district = district,
        market = market,
        lastCommodity = lastCommodity
    )

    /**
     * Calculate profile completion percentage based on filled fields.
     * Returns a value between 0 and 100.
     */
    fun getCompletionPercentage(): Int {
        var filledFields = 0
        val totalFields = 10 // name, state, district, village, tehsil, totalFarmAcres, irrigationAvailable, mobileNumber, emailId, majorCrops

        if (!name.isNullOrBlank()) filledFields++
        if (state.isNotBlank()) filledFields++
        if (district.isNotBlank()) filledFields++
        if (!village.isNullOrBlank()) filledFields++
        if (!tehsil.isNullOrBlank()) filledFields++
        if (totalFarmAcres != null && totalFarmAcres > 0) filledFields++
        if (irrigationAvailable != null) filledFields++
        if (!mobileNumber.isNullOrBlank()) filledFields++
        if (!emailId.isNullOrBlank()) filledFields++
        if (majorCrops.isNotEmpty()) filledFields++

        return (filledFields * 100) / totalFields
    }
}
