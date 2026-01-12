package com.maswadkar.developers.androidify.data

/**
 * Data class representing a knowledge document (PDF) for a specific crop
 * Supports multi-language titles and descriptions via maps
 */
data class KnowledgeDocument(
    val id: String = "",
    val title: String = "", // Default/fallback title (English)
    val titles: Map<String, String> = emptyMap(), // Language code -> localized title
    val description: String = "", // Default/fallback description (English)
    val descriptions: Map<String, String> = emptyMap(), // Language code -> localized description
    val storagePath: String = "",
    val cropId: String = "",
    val displayOrder: Int = 0
) {
    /**
     * Get the localized title for the given language code.
     * Falls back to default title if translation not available.
     */
    fun getLocalizedTitle(languageCode: String): String {
        return titles[languageCode] ?: title
    }

    /**
     * Get the localized description for the given language code.
     * Falls back to default description if translation not available.
     */
    fun getLocalizedDescription(languageCode: String): String {
        return descriptions[languageCode] ?: description
    }
}

