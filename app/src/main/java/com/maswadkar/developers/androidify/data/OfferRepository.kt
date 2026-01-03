package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching offers and supplier data from Firestore
 */
class OfferRepository {

    companion object {
        private const val TAG = "OfferRepository"
        private const val OFFERS_COLLECTION = "offers"
        private const val SUPPLIERS_COLLECTION = "suppliers"

        @Volatile
        private var instance: OfferRepository? = null

        fun getInstance(): OfferRepository {
            return instance ?: synchronized(this) {
                instance ?: OfferRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Sort options for offers
     */
    enum class SortField {
        PRICE_NORMALIZED,
        CREATED_AT
    }

    enum class SortDirection {
        ASCENDING,
        DESCENDING
    }

    /**
     * Fetch approved offers with optional filters and sorting
     */
    suspend fun getOffers(
        districtId: String? = null,
        category: String? = null,
        sortField: SortField = SortField.CREATED_AT,
        sortDirection: SortDirection = SortDirection.DESCENDING
    ): List<Offer> {
        return try {
            Log.d(TAG, "Fetching offers - districtId: $districtId, category: $category, sortField: $sortField, sortDirection: $sortDirection")

            // Build query with required filters for security rules
            var query: Query = firestore.collection(OFFERS_COLLECTION)
                .whereEqualTo("status", "APPROVED")
                .whereEqualTo("supplierApproved", true)

            // Apply optional filters
            if (!districtId.isNullOrEmpty()) {
                query = query.whereEqualTo("districtId", districtId)
            }

            if (!category.isNullOrEmpty()) {
                query = query.whereEqualTo("category", category)
            }

            val snapshot = query.get().await()

            Log.d(TAG, "Query returned ${snapshot.documents.size} documents")

            var offers = snapshot.documents.mapNotNull { doc ->
                try {
                    Log.d(TAG, "Parsing document: ${doc.id}")
                    doc.toObject(Offer::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing offer document ${doc.id}: ${e.message}")
                    null
                }
            }

            // Sort locally
            offers = when (sortField) {
                SortField.PRICE_NORMALIZED -> {
                    if (sortDirection == SortDirection.ASCENDING) {
                        offers.sortedBy { it.priceNormalized }
                    } else {
                        offers.sortedByDescending { it.priceNormalized }
                    }
                }
                SortField.CREATED_AT -> {
                    if (sortDirection == SortDirection.ASCENDING) {
                        offers.sortedBy { it.createdAt?.seconds ?: 0 }
                    } else {
                        offers.sortedByDescending { it.createdAt?.seconds ?: 0 }
                    }
                }
            }

            Log.d(TAG, "Returning ${offers.size} offers")
            offers
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching offers: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetch supplier details by ID
     */
    suspend fun getSupplier(supplierId: String): Supplier? {
        return try {
            val doc = firestore.collection(SUPPLIERS_COLLECTION)
                .document(supplierId)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(Supplier::class.java)
            } else {
                Log.w(TAG, "Supplier not found: $supplierId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching supplier: ${e.message}")
            null
        }
    }
}

