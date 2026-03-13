package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp

/**
 * Comprehensive farmer profile containing location preferences, farm details, and contact info.
 * Extends the previous MandiPreferences with additional fields for personalization.
 */
data class FarmerProfile(
    // Identity
    val name: String? = null,

    // Location fields (migrated from MandiPreferences)
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
            totalFarmAcres != null && totalFarmAcres > 0

    /**
     * Return the missing lead-required fields for UI prompts.
     */
    fun getMissingLeadFields(): List<String> = buildList {
        if (name.isNullOrBlank()) add("name")
        if (village.isNullOrBlank()) add("village")
        if (tehsil.isNullOrBlank()) add("tehsil")
        if (district.isBlank()) add("district")
        if (totalFarmAcres == null || totalFarmAcres <= 0) add("totalFarmAcres")
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
        mobileNumber = mobileNumber?.trim()?.takeIf { it.isNotBlank() },
        emailId = emailId?.trim()?.takeIf { it.isNotBlank() }
    )

    /**
     * Convert to MandiPreferences for backward compatibility
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

    companion object {
        /**
         * Create a FarmerProfile from existing MandiPreferences (for migration)
         */
        fun fromMandiPreferences(prefs: MandiPreferences): FarmerProfile = FarmerProfile(
            state = prefs.state,
            district = prefs.district,
            market = prefs.market,
            lastCommodity = prefs.lastCommodity
        )
    }
}
