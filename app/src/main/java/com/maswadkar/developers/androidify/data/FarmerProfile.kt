package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp

/**
 * Comprehensive farmer profile containing location preferences, farm details, and contact info.
 * Extends the previous MandiPreferences with additional fields for personalization.
 */
data class FarmerProfile(
    // Location fields (migrated from MandiPreferences)
    val state: String = "",
    val district: String = "",
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
    constructor() : this("", "", null, null, null, null, null, null, null, null, null, emptyList(), null)

    /**
     * Check if the profile has valid location data (minimum requirement)
     */
    fun hasValidLocation(): Boolean = state.isNotBlank() && district.isNotBlank()

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
        val totalFields = 7 // state, district, totalFarmAcres, irrigationAvailable, mobileNumber, emailId, majorCrops

        if (state.isNotBlank()) filledFields++
        if (district.isNotBlank()) filledFields++
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

