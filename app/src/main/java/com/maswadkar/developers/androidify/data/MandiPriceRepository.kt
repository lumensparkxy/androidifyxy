package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching Mandi price data from Firestore
 */
class MandiPriceRepository {

    companion object {
        private const val TAG = "MandiPriceRepository"
        private const val COLLECTION_NAME = "mandi_prices"

        @Volatile
        private var instance: MandiPriceRepository? = null

        fun getInstance(): MandiPriceRepository {
            return instance ?: synchronized(this) {
                instance ?: MandiPriceRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val districtsCache = mutableMapOf<String, List<String>>()

    private fun normalizePreferences(preferences: MandiPreferences): MandiPreferences = MandiPreferences(
        state = preferences.state.trim(),
        district = preferences.district.trim(),
        market = preferences.market?.trim()?.takeIf { it.isNotBlank() },
        lastCommodity = preferences.lastCommodity?.trim()?.takeIf { it.isNotBlank() }
    )

    private fun buildFarmerProfilePreferencePatch(preferences: MandiPreferences): Map<String, Any?> {
        val normalizedPreferences = normalizePreferences(preferences)
        return mapOf(
            "state" to normalizedPreferences.state,
            "district" to normalizedPreferences.district,
            "market" to normalizedPreferences.market,
            "lastCommodity" to normalizedPreferences.lastCommodity,
            "updatedAt" to Timestamp.now()
        )
    }

    /**
     * Get all distinct states
     */
    suspend fun getStates(): List<String> {
        return try {
            val snapshot = firestore.collection(COLLECTION_NAME)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { it.getString("state") }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching states: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get districts for a given state
     */
    suspend fun getDistricts(state: String): List<String> {
        if (districtsCache.containsKey(state)) {
            return districtsCache[state] ?: emptyList()
        }

        return try {
            val snapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("state", state)
                .get()
                .await()

            val districts = snapshot.documents
                .mapNotNull { it.getString("district") }
                .distinct()
                .sorted()

            if (districts.isNotEmpty()) {
                districtsCache[state] = districts
            }
            districts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching districts: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get commodities for a given state and district
     */
    suspend fun getCommodities(state: String, district: String? = null): List<String> {
        return try {
            var query: Query = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("state", state)

            if (district != null) {
                query = query.whereEqualTo("district", district)
            }

            val snapshot = query.get().await()

            snapshot.documents
                .mapNotNull { it.getString("commodity") }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching commodities: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get markets for a given state and district
     */
    suspend fun getMarkets(state: String, district: String): List<String> {
        return try {
            val snapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("state", state)
                .whereEqualTo("district", district)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { it.getString("market") }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching markets: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get filtered mandi prices - sorted by date descending (newest first)
     * Returns last 7 days of data for better context
     */
    suspend fun getMandiPrices(
        state: String? = null,
        district: String? = null,
        market: String? = null,
        commodity: String? = null,
        limit: Int = 100
    ): List<MandiPrice> {
        return try {
            var query: Query = firestore.collection(COLLECTION_NAME)

            state?.let { query = query.whereEqualTo("state", it) }
            district?.let { query = query.whereEqualTo("district", it) }
            market?.let { query = query.whereEqualTo("market", it) }
            commodity?.let { query = query.whereEqualTo("commodity", it) }

            // Order by arrival_date_parsed descending to get newest first
            query = query.orderBy("arrival_date_parsed", Query.Direction.DESCENDING)

            val snapshot = query.limit(limit.toLong()).get().await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(MandiPrice::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing document ${doc.id}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching mandi prices: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get the data date (most recent arrival date in the dataset)
     */
    suspend fun getLatestDataDate(state: String, district: String): String? {
        return try {
            val snapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("state", state)
                .whereEqualTo("district", district)
                .orderBy("arrival_date_parsed", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.getString("arrival_date")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest data date: ${e.message}")
            null
        }
    }


    /**
     * Get user's saved Mandi preferences
     */
    suspend fun getUserPreferences(userId: String): MandiPreferences? {
        return try {
            val profileDoc = firestore.farmerProfileDocument(userId)
                .get()
                .await()

            if (profileDoc.exists()) {
                profileDoc.toObject(FarmerProfile::class.java)
                    ?.normalized()
                    ?.toMandiPreferences()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user preferences: ${e.message}")
            null
        }
    }

    /**
     * Save user's Mandi preferences
     */
    suspend fun saveUserPreferences(userId: String, preferences: MandiPreferences): Boolean {
        return try {
            val normalizedPreferences = normalizePreferences(preferences)
            firestore.farmerProfileDocument(userId)
                .set(buildFarmerProfilePreferencePatch(normalizedPreferences), SetOptions.merge())
                .await()
            Log.d(TAG, "Saved user preferences to farmer_profile: $normalizedPreferences")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user preferences: ${e.message}")
            false
        }
    }

    /**
     * Update last searched commodity in preferences
     */
    suspend fun updateLastCommodity(userId: String, commodity: String): Boolean {
        return try {
            firestore.farmerProfileDocument(userId)
                .set(
                    mapOf(
                        "lastCommodity" to commodity.trim(),
                        "updatedAt" to Timestamp.now()
                    ),
                    SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last commodity: ${e.message}")
            false
        }
    }
}
