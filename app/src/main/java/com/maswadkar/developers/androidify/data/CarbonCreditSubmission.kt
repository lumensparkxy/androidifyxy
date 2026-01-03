package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp

/**
 * Data class representing a carbon credit form submission
 */
data class CarbonCreditSubmission(
    val userId: String = "",
    val landSizeAcres: Double = 0.0,
    val bigTreeCount: Int = 0,
    val interestedInGreenFarming: Boolean = false,
    val phoneNumber: String = "",
    val createdAt: Timestamp? = null
) {
    // No-arg constructor for Firestore
    constructor() : this("", 0.0, 0, false, "", null)
}

