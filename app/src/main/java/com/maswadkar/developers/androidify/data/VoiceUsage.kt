package com.maswadkar.developers.androidify.data

import com.google.firebase.firestore.DocumentId

/**
 * Data class representing a user's voice API usage tracking.
 * Stored in Firestore collection "voice_usage" with document ID = userId.
 */
data class VoiceUsage(
    @DocumentId
    val id: String = "",

    /** Total minutes used in the current period */
    val minutesUsed: Double = 0.0,

    /** Number of voice sessions started in the current period */
    val sessionsUsed: Int = 0,

    /** Month of last reset (1-12) */
    val lastResetMonth: Int = 0,

    /** Year of last reset (e.g., 2026) */
    val lastResetYear: Int = 0
) {
    companion object {
        /**
         * Default monthly limit in minutes.
         * Easy to change this value to adjust the free tier limit.
         */
        const val DEFAULT_MONTHLY_LIMIT_MINUTES = 5.0

        /**
         * Collection name in Firestore
         */
        const val COLLECTION_NAME = "voice_usage"
    }

    /**
     * Calculate remaining minutes for the current period
     */
    fun remainingMinutes(limit: Double = DEFAULT_MONTHLY_LIMIT_MINUTES): Double {
        return (limit - minutesUsed).coerceAtLeast(0.0)
    }

    /**
     * Check if user can start a new voice session
     */
    fun canStartSession(limit: Double = DEFAULT_MONTHLY_LIMIT_MINUTES): Boolean {
        return minutesUsed < limit
    }
}

