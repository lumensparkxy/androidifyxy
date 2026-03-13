package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing farmer profile data in Firestore.
 * Handles silent migration from old MandiPreferences to new FarmerProfile.
 */
class FarmerProfileRepository {

    companion object {
        private const val TAG = "FarmerProfileRepository"
        private const val USERS_COLLECTION = "users"
        private const val SETTINGS_COLLECTION = "settings"
        private const val FARMER_PROFILE_DOC = "farmer_profile"
        private const val MANDI_PREFERENCES_DOC = "mandi_preferences"

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
     * If profile doesn't exist but old mandi_preferences does, silently migrate.
     */
    suspend fun getUserProfile(userId: String): FarmerProfile? {
        return try {
            val profileDoc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(FARMER_PROFILE_DOC)
                .get()
                .await()

            if (profileDoc.exists()) {
                profileDoc.toObject(FarmerProfile::class.java)
            } else {
                // Try to migrate from old mandi_preferences
                migrateFromMandiPreferences(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile: ${e.message}")
            null
        }
    }

    /**
     * Migrate existing mandi_preferences to farmer_profile (silent migration)
     */
    private suspend fun migrateFromMandiPreferences(userId: String): FarmerProfile? {
        return try {
            val prefsDoc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(MANDI_PREFERENCES_DOC)
                .get()
                .await()

            if (prefsDoc.exists()) {
                val oldPrefs = prefsDoc.toObject(MandiPreferences::class.java)
                if (oldPrefs != null) {
                    val newProfile = FarmerProfile.fromMandiPreferences(oldPrefs).copy(
                        updatedAt = Timestamp.now()
                    )
                    // Save the migrated profile
                    saveUserProfile(userId, newProfile)
                    Log.d(TAG, "Migrated mandi_preferences to farmer_profile for user: $userId")
                    return newProfile
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating from mandi_preferences: ${e.message}")
            null
        }
    }

    /**
     * Save user's farmer profile
     */
    suspend fun saveUserProfile(userId: String, profile: FarmerProfile): Boolean {
        return try {
            val profileWithTimestamp = profile.normalized().copy(updatedAt = Timestamp.now())
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(FARMER_PROFILE_DOC)
                .set(profileWithTimestamp)
                .await()

            // Also update mandi_preferences for backward compatibility with MandiPricesViewModel
            val mandiPrefs = profileWithTimestamp.toMandiPreferences()
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(MANDI_PREFERENCES_DOC)
                .set(mandiPrefs)
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
                "lastCommodity" to commodity,
                "updatedAt" to Timestamp.now()
            )
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(FARMER_PROFILE_DOC)
                .update(updates)
                .await()

            // Also update mandi_preferences for backward compatibility
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SETTINGS_COLLECTION)
                .document(MANDI_PREFERENCES_DOC)
                .update("lastCommodity", commodity)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last commodity: ${e.message}")
            false
        }
    }
}
