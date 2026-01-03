package com.maswadkar.developers.androidify.data

import com.google.firebase.firestore.DocumentId

/**
 * Represents a supplier profile
 */
data class Supplier(
    @DocumentId
    val id: String = "",
    val businessName: String = "",
    val phone: String = "",
    val address: String = "",
    val verificationStatus: String = ""
)

