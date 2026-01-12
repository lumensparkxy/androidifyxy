package com.maswadkar.developers.androidify.data

/**
 * Data class representing a crop category in the Knowledge Base
 * Supports multi-language names via the names map
 */
data class Crop(
    val id: String = "",
    val name: String = "", // Default/fallback name (English)
    val names: Map<String, String> = emptyMap(), // Language code -> localized name (e.g., "hi" -> "मक्का")
    val iconUrl: String = "",
    val displayOrder: Int = 0
) {
    /**
     * Get the localized name for the given language code.
     * Falls back to default name if translation not available.
     */
    fun getLocalizedName(languageCode: String): String {
        return names[languageCode] ?: name
    }
}

