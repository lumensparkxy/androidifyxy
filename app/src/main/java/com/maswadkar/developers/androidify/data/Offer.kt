package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Represents an offer from a supplier
 */
data class Offer(
    @DocumentId
    val id: String = "",
    val category: String = "",
    val createdAt: Timestamp? = null,
    val districtId: String = "",
    val productName: String = "",
    val packSize: Double = 0.0,
    val packUnit: String = "",
    val priceRetail: Double = 0.0,
    val priceNormalized: Double = 0.0,
    val supplierName: String = "",
    val supplierId: String = "",
    val status: String = "",
    val supplierApproved: Boolean = false,
    val npkRaw: String? = null,
    val updatedAt: Timestamp? = null
)

