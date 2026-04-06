package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing farmer profile data in Firestore.
 */
class FarmerProfileRepository {

    companion object {
        private const val TAG = "FarmerProfileRepository"

        @Volatile
        private var instance: FarmerProfileRepository? = null

        fun getInstance(): FarmerProfileRepository {
            return instance ?: synchronized(this) {
                instance ?: FarmerProfileRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Get user's farmer profile.
     */
    suspend fun getUserProfile(userId: String): FarmerProfile? {
        return try {
            val profileDoc = firestore.farmerProfileDocument(userId)
                .get()
                .await()

            if (profileDoc.exists()) {
                profileDoc.toObject(FarmerProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile: ${e.message}")
            null
        }
    }

    /**
     * Save user's farmer profile
     */
    suspend fun saveUserProfile(userId: String, profile: FarmerProfile): Boolean {
        return try {
            val profileWithTimestamp = profile.normalized().copy(updatedAt = Timestamp.now())
            firestore.farmerProfileDocument(userId)
                .set(profileWithTimestamp)
                .await()

            Log.d(TAG, "Saved user profile: $profileWithTimestamp")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile: ${e.message}")
            false
        }
    }

    /**
     * Update last searched commodity in profile
     */
    suspend fun updateLastCommodity(userId: String, commodity: String): Boolean {
        return try {
            val updates = mapOf(
                "lastCommodity" to commodity.trim(),
                "updatedAt" to Timestamp.now()
            )
            firestore.farmerProfileDocument(userId)
                .set(updates, SetOptions.merge())
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last commodity: ${e.message}")
            false
        }
    }
}
