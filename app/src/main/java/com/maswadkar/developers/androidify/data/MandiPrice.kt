package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Represents a commodity price record from a Mandi (market)
 */
data class MandiPrice(
    @DocumentId
    val id: String = "",
    val state: String = "",
    val district: String = "",
    val market: String = "",
    val commodity: String = "",
    val variety: String = "",
    val grade: String = "",
    @get:PropertyName("arrival_date") @set:PropertyName("arrival_date")
    var arrivalDate: String = "",
    @get:PropertyName("arrival_date_parsed") @set:PropertyName("arrival_date_parsed")
    var arrivalDateParsed: Timestamp? = null,
    @get:PropertyName("min_price") @set:PropertyName("min_price")
    var minPrice: Double = 0.0,
    @get:PropertyName("max_price") @set:PropertyName("max_price")
    var maxPrice: Double = 0.0,
    @get:PropertyName("modal_price") @set:PropertyName("modal_price")
    var modalPrice: Double = 0.0
) {
    // No-arg constructor for Firestore
    constructor() : this("", "", "", "", "", "", "", "", null, 0.0, 0.0, 0.0)
}

