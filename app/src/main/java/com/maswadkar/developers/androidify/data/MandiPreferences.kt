package com.maswadkar.developers.androidify.data

/**
 * Compact mandi/location preference projection.
 *
 * This is not the primary persisted user profile document; `farmer_profile`
 * is the canonical source of truth and this shape is retained for compact mandi UI state.
 */
data class MandiPreferences(
    val state: String = "",
    val district: String = "",
    val market: String? = null,
    val lastCommodity: String? = null
) {
    // No-arg constructor for Firestore
    constructor() : this("", "", null, null)

    fun isValid(): Boolean = state.isNotBlank() && district.isNotBlank()
}

