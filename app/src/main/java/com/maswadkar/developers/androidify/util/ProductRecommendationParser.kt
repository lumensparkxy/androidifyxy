package com.maswadkar.developers.androidify.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.maswadkar.developers.androidify.data.ProductRecommendation

/**
 * Parser for extracting product recommendations from AI responses.
 *
 * The AI is instructed to append product recommendations in a fenced code block:
 * ```krishi_products
 * [{"name": "...", "type": "pesticide", "quantity": "...", "unit": "...", "reason": "..."}]
 * ```
 *
 * This parser extracts the JSON, parses it, and returns both the clean display text
 * (without the code block) and the list of recommendations.
 */
object ProductRecommendationParser {

    private const val TAG = "ProductRecommendationParser"

    // Regex to match the fenced code block with product recommendations
    private val PRODUCTS_BLOCK_REGEX = Regex(
        """```krishi_products\s*\n([\s\S]*?)\n?```""",
        RegexOption.MULTILINE
    )

    private val gson = Gson()

    /**
     * Result of parsing an AI response
     * @param displayText The text to display (with product block removed)
     * @param recommendations List of parsed product recommendations (empty if none found or parsing failed)
     */
    data class ParseResult(
        val displayText: String,
        val recommendations: List<ProductRecommendation>
    )

    /**
     * Parse an AI response to extract product recommendations.
     *
     * @param responseText The raw AI response text
     * @return ParseResult containing clean display text and list of recommendations
     */
    fun parse(responseText: String): ParseResult {
        val matchResult = PRODUCTS_BLOCK_REGEX.find(responseText)

        if (matchResult == null) {
            // No product block found, return original text
            return ParseResult(
                displayText = responseText,
                recommendations = emptyList()
            )
        }

        // Extract the JSON content from the code block
        val jsonContent = matchResult.groupValues[1].trim()

        // Remove the code block from display text
        val displayText = responseText
            .replace(matchResult.value, "")
            .trim()

        // Parse the JSON
        val recommendations = try {
            val listType = object : TypeToken<List<ProductRecommendation>>() {}.type
            gson.fromJson<List<ProductRecommendation>>(jsonContent, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse product recommendations: ${e.message}")
            Log.d(TAG, "JSON content was: $jsonContent")
            emptyList()
        }

        return ParseResult(
            displayText = displayText,
            recommendations = recommendations
        )
    }

    /**
     * Check if a response contains product recommendations without fully parsing
     */
    fun hasRecommendations(responseText: String): Boolean {
        return PRODUCTS_BLOCK_REGEX.containsMatchIn(responseText)
    }
}
