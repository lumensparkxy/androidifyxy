package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Repository for tracking supplier contact clicks (WhatsApp/Call)
 * Used for monetization analytics - pay-per-lead model
 */
class ClickTrackingRepository {

    companion object {
        private const val TAG = "ClickTrackingRepository"
        private const val COLLECTION_NAME = "supplier_clicks"

        @Volatile
        private var instance: ClickTrackingRepository? = null

        fun getInstance(): ClickTrackingRepository {
            return instance ?: synchronized(this) {
                instance ?: ClickTrackingRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Click types for tracking
     */
    enum class ClickType {
        WHATSAPP,
        CALL
    }

    /**
     * Record a click event for a supplier contact button
     * Fire-and-forget - does not block UI
     *
     * @param supplierId The supplier whose contact was clicked
     * @param offerId The offer context where the click happened
     * @param clickType WHATSAPP or CALL
     */
    fun recordClick(supplierId: String, offerId: String, clickType: ClickType) {
        // Fire-and-forget on IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clickData = hashMapOf(
                    "supplierId" to supplierId,
                    "offerId" to offerId,
                    "clickType" to clickType.name,
                    "userId" to auth.currentUser?.uid, // null if anonymous (for debugging)
                    "timestamp" to Timestamp.now()
                )

                firestore.collection(COLLECTION_NAME)
                    .add(clickData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Click recorded: $clickType for supplier $supplierId")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to record click: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording click: ${e.message}")
            }
        }
    }
}

