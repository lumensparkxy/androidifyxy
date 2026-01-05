package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for submitting carbon credit form data to Firestore
 */
class CarbonCreditsRepository {

    companion object {
        private const val TAG = "CarbonCreditsRepository"
        private const val COLLECTION_NAME = "carbon_credits"

        @Volatile
        private var instance: CarbonCreditsRepository? = null

        fun getInstance(): CarbonCreditsRepository {
            return instance ?: synchronized(this) {
                instance ?: CarbonCreditsRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Submit a carbon credit form to Firestore
     * @param submission The form data to submit
     * @return Result indicating success or failure
     */
    suspend fun submitCarbonCreditForm(submission: CarbonCreditSubmission): Result<Unit> {
        return try {
            Log.d(TAG, "Submitting carbon credit form for user: ${submission.userId}")

            val submissionWithTimestamp = submission.copy(
                createdAt = Timestamp.now()
            )

            firestore.collection(COLLECTION_NAME)
                .add(submissionWithTimestamp)
                .await()

            Log.d(TAG, "Carbon credit form submitted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting carbon credit form: ${e.message}", e)
            Result.failure(e)
        }
    }
}

