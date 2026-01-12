package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching knowledge base data (crops and documents) from Firestore and Storage
 */
class KnowledgeRepository {

    companion object {
        private const val TAG = "KnowledgeRepository"
        private const val CROPS_COLLECTION = "knowledge_crops"
        private const val DOCUMENTS_COLLECTION = "knowledge_documents"

        @Volatile
        private var instance: KnowledgeRepository? = null

        fun getInstance(): KnowledgeRepository {
            return instance ?: synchronized(this) {
                instance ?: KnowledgeRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    /**
     * Fetch all crops for the knowledge base
     */
    suspend fun getCrops(): List<Crop> {
        return try {
            Log.d(TAG, "Fetching crops from Firestore")

            val snapshot = firestore.collection(CROPS_COLLECTION)
                .get()
                .await()

            Log.d(TAG, "Got ${snapshot.documents.size} documents from Firestore")

            snapshot.documents.forEach { doc ->
                Log.d(TAG, "Document ${doc.id}: ${doc.data}")
            }

            val crops = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Crop::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing crop document ${doc.id}: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Fetched ${crops.size} crops")
            crops.sortedBy { it.displayOrder }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching crops: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetch documents for a specific crop
     */
    suspend fun getDocuments(cropId: String): List<KnowledgeDocument> {
        return try {
            Log.d(TAG, "Fetching documents for crop: $cropId")

            val snapshot = firestore.collection(DOCUMENTS_COLLECTION)
                .whereEqualTo("cropId", cropId)
                .get()
                .await()

            Log.d(TAG, "Got ${snapshot.documents.size} documents from Firestore")

            val documents = snapshot.documents.mapNotNull { doc ->
                try {
                    Log.d(TAG, "Document ${doc.id}: ${doc.data}")
                    doc.toObject(KnowledgeDocument::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing document ${doc.id}: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Fetched ${documents.size} documents for crop $cropId")
            documents.sortedBy { it.displayOrder }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching documents: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get download URL for a document stored in Firebase Storage
     */
    suspend fun getDocumentDownloadUrl(storagePath: String): String? {
        return try {
            Log.d(TAG, "Getting download URL for: $storagePath")
            val storageRef = storage.reference.child(storagePath)
            val url = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "Download URL: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL: ${e.message}", e)
            null
        }
    }
}

