package com.maswadkar.developers.androidify.data

/**
 * User's saved Mandi location preferences
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

