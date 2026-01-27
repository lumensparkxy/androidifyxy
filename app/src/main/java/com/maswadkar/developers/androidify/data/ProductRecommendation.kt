package com.maswadkar.developers.androidify.data

import com.google.gson.annotations.SerializedName

/**
 * Represents a product recommendation extracted from AI responses.
 * When the AI suggests pesticides, fertilizers, equipment, seeds, etc.,
 * these are parsed and displayed as actionable tiles.
 */
data class ProductRecommendation(
    @SerializedName("name")
    val productName: String,

    @SerializedName("type")
    val productType: String, // Will be converted to ProductType enum

    @SerializedName("quantity")
    val quantity: String? = null,

    @SerializedName("unit")
    val unit: String? = null,

    @SerializedName("reason")
    val reason: String? = null
) {
    /**
     * Get the product type as enum, with fallback to OTHER
     */
    fun getProductTypeEnum(): ProductType {
        return ProductType.fromString(productType)
    }
}

/**
 * Types of products that can be recommended
 */
enum class ProductType(val jsonValue: String) {
    PESTICIDE("pesticide"),
    FERTILIZER("fertilizer"),
    EQUIPMENT("equipment"),
    SEED("seed"),
    OTHER("other");

    companion object {
        fun fromString(value: String?): ProductType {
            return entries.find { it.jsonValue.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}
