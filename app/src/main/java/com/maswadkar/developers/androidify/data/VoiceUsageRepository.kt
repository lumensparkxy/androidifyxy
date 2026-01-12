package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Repository for managing voice API usage tracking in Firestore.
 * Handles monthly quota tracking with automatic reset on new month.
 */
class VoiceUsageRepository {

    companion object {
        private const val TAG = "VoiceUsageRepository"

        @Volatile
        private var instance: VoiceUsageRepository? = null

        fun getInstance(): VoiceUsageRepository {
            return instance ?: synchronized(this) {
                instance ?: VoiceUsageRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Get real-time updates of user's voice usage.
     * Automatically resets counters if a new month has started.
     */
    fun getUsageFlow(userId: String): Flow<VoiceUsage> = callbackFlow {
        val docRef = firestore.collection(VoiceUsage.COLLECTION_NAME).document(userId)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to voice usage: ${error.message}")
                return@addSnapshotListener
            }

            val usage = if (snapshot?.exists() == true) {
                snapshot.toObject(VoiceUsage::class.java) ?: VoiceUsage()
            } else {
                VoiceUsage()
            }

            // Check if we need to reset for a new month
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-indexed
            val currentYear = calendar.get(Calendar.YEAR)

            val effectiveUsage = if (shouldResetUsage(usage, currentMonth, currentYear)) {
                // Return zeroed usage (will be persisted on next session end)
                VoiceUsage(
                    id = userId,
                    minutesUsed = 0.0,
                    sessionsUsed = 0,
                    lastResetMonth = currentMonth,
                    lastResetYear = currentYear
                )
            } else {
                usage
            }

            trySend(effectiveUsage)
        }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Stopped listening to voice usage")
        }
    }

    /**
     * Check if user can start a new voice session.
     * Returns true if user has remaining quota.
     */
    suspend fun canStartSession(userId: String): Boolean {
        return try {
            val usage = getUsage(userId)
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            // If new month, user can start (quota will be reset)
            if (shouldResetUsage(usage, currentMonth, currentYear)) {
                true
            } else {
                usage.canStartSession()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session eligibility: ${e.message}")
            // On error, allow the session (fail open)
            true
        }
    }

    /**
     * Record usage from a completed voice session.
     * @param userId The user's ID
     * @param durationMinutes Duration of the session in minutes
     */
    suspend fun recordSessionUsage(userId: String, durationMinutes: Double) {
        try {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            val currentUsage = getUsage(userId)

            // Check if we need to reset for new month
            val baseUsage = if (shouldResetUsage(currentUsage, currentMonth, currentYear)) {
                VoiceUsage(
                    id = userId,
                    minutesUsed = 0.0,
                    sessionsUsed = 0,
                    lastResetMonth = currentMonth,
                    lastResetYear = currentYear
                )
            } else {
                currentUsage
            }

            // Update with new session data
            val updatedUsage = hashMapOf(
                "minutesUsed" to baseUsage.minutesUsed + durationMinutes,
                "sessionsUsed" to baseUsage.sessionsUsed + 1,
                "lastResetMonth" to currentMonth,
                "lastResetYear" to currentYear
            )

            firestore.collection(VoiceUsage.COLLECTION_NAME)
                .document(userId)
                .set(updatedUsage)
                .await()

            Log.d(TAG, "Recorded session usage: +${durationMinutes} min for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording session usage: ${e.message}")
            // Don't throw - we don't want to crash the app if usage tracking fails
        }
    }

    /**
     * Get current usage for a user (one-time fetch).
     */
    private suspend fun getUsage(userId: String): VoiceUsage {
        return try {
            val doc = firestore.collection(VoiceUsage.COLLECTION_NAME)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(VoiceUsage::class.java) ?: VoiceUsage()
            } else {
                VoiceUsage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching usage: ${e.message}")
            VoiceUsage()
        }
    }

    /**
     * Check if usage should be reset based on current month/year.
     */
    private fun shouldResetUsage(usage: VoiceUsage, currentMonth: Int, currentYear: Int): Boolean {
        // Reset if year changed or month changed
        return usage.lastResetYear < currentYear ||
                (usage.lastResetYear == currentYear && usage.lastResetMonth < currentMonth) ||
                (usage.lastResetMonth == 0 && usage.lastResetYear == 0) // First time usage
    }
}

