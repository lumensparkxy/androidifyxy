package com.maswadkar.developers.androidify.data

/**
 * Static filter options for offers
 */
object OfferFilters {

    /**
     * Maharashtra districts with their IDs
     * Format: "MH:district_name_lowercase"
     */
    val districts: List<FilterOption> = listOf(
        FilterOption("MH:ahmednagar", "Ahmednagar"),
        FilterOption("MH:akola", "Akola"),
        FilterOption("MH:amravati", "Amravati"),
        FilterOption("MH:aurangabad", "Aurangabad"),
        FilterOption("MH:beed", "Beed"),
        FilterOption("MH:bhandara", "Bhandara"),
        FilterOption("MH:buldhana", "Buldhana"),
        FilterOption("MH:chandrapur", "Chandrapur"),
        FilterOption("MH:dhule", "Dhule"),
        FilterOption("MH:gadchiroli", "Gadchiroli"),
        FilterOption("MH:gondia", "Gondia"),
        FilterOption("MH:hingoli", "Hingoli"),
        FilterOption("MH:jalgaon", "Jalgaon"),
        FilterOption("MH:jalna", "Jalna"),
        FilterOption("MH:kolhapur", "Kolhapur"),
        FilterOption("MH:latur", "Latur"),
        FilterOption("MH:mumbai", "Mumbai"),
        FilterOption("MH:mumbai_suburban", "Mumbai Suburban"),
        FilterOption("MH:nagpur", "Nagpur"),
        FilterOption("MH:nanded", "Nanded"),
        FilterOption("MH:nandurbar", "Nandurbar"),
        FilterOption("MH:nashik", "Nashik"),
        FilterOption("MH:osmanabad", "Osmanabad"),
        FilterOption("MH:palghar", "Palghar"),
        FilterOption("MH:parbhani", "Parbhani"),
        FilterOption("MH:pune", "Pune"),
        FilterOption("MH:raigad", "Raigad"),
        FilterOption("MH:ratnagiri", "Ratnagiri"),
        FilterOption("MH:sangli", "Sangli"),
        FilterOption("MH:satara", "Satara"),
        FilterOption("MH:sindhudurg", "Sindhudurg"),
        FilterOption("MH:solapur", "Solapur"),
        FilterOption("MH:thane", "Thane"),
        FilterOption("MH:wardha", "Wardha"),
        FilterOption("MH:washim", "Washim"),
        FilterOption("MH:yavatmal", "Yavatmal")
    )

    /**
     * Product categories
     */
    val categories: List<FilterOption> = listOf(
        FilterOption("fertilizer", "Fertilizer"),
        FilterOption("seeds", "Seeds"),
        FilterOption("pesticides", "Pesticides"),
        FilterOption("equipment", "Equipment"),
        FilterOption("other", "Other")
    )

    /**
     * Get display name for a district ID
     */
    fun getDistrictDisplayName(districtId: String): String {
        return districts.find { it.id == districtId }?.displayName ?: districtId
    }

    /**
     * Get display name for a category
     */
    fun getCategoryDisplayName(category: String): String {
        return categories.find { it.id == category }?.displayName ?: category.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Represents a filter option with ID and display name
 */
data class FilterOption(
    val id: String,
    val displayName: String
)

