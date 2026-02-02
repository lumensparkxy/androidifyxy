package com.maswadkar.developers.androidify.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing chat conversations in Firestore
 */
class ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val CONVERSATIONS_COLLECTION = "conversations"

        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    init {
        // Enable offline persistence with unlimited cache size
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(persistentCacheSettings { })
            .build()
        firestore.firestoreSettings = settings
        Log.d(TAG, "Firestore initialized with offline persistence enabled")
    }

    /**
     * Save or update a conversation.
     * Handles both auto-generated Firestore IDs and pre-generated UUIDs for image storage paths.
     *
     * @param conversation The conversation to save
     * @param isNew Whether this is a new conversation (uses set instead of update for pre-generated IDs)
     */
    suspend fun saveConversation(conversation: Conversation, isNew: Boolean = false): String {
        return try {
            val conversationData = hashMapOf(
                "userId" to conversation.userId,
                "title" to conversation.title,
                "messages" to conversation.messages.map { msg ->
                    hashMapOf(
                        "text" to msg.text,
                        "isUser" to msg.isUser,
                        "timestamp" to msg.timestamp,
                        "imageUrl" to msg.imageUrl
                    )
                },
                "updatedAt" to Timestamp.now()
            )

            if (conversation.id.isEmpty()) {
                // New conversation without pre-generated ID (legacy path)
                conversationData["createdAt"] = Timestamp.now()
                val docRef = firestore.collection(CONVERSATIONS_COLLECTION)
                    .add(conversationData)
                    .await()
                Log.d(TAG, "Created new conversation (auto-ID): ${docRef.id}")
                docRef.id
            } else if (isNew) {
                // New conversation with pre-generated UUID (for image storage path consistency)
                conversationData["createdAt"] = Timestamp.now()
                firestore.collection(CONVERSATIONS_COLLECTION)
                    .document(conversation.id)
                    .set(conversationData)
                    .await()
                Log.d(TAG, "Created new conversation (pre-generated ID): ${conversation.id}")
                conversation.id
            } else {
                // Update existing conversation
                firestore.collection(CONVERSATIONS_COLLECTION)
                    .document(conversation.id)
                    .update(conversationData as Map<String, Any>)
                    .await()
                Log.d(TAG, "Updated conversation: ${conversation.id}")
                conversation.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving conversation: ${e.message}")
            throw e
        }
    }

    /**
     * Get real-time updates of user's conversations
     */
    fun getConversationsFlow(userId: String): Flow<List<Conversation>> = callbackFlow {
        val query = firestore.collection(CONVERSATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to conversations: ${error.message}")
                return@addSnapshotListener
            }

            val conversations = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject(Conversation::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing conversation: ${e.message}")
                    null
                }
            } ?: emptyList()

            trySend(conversations)
        }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Stopped listening to conversations")
        }
    }

    /**
     * Load a single conversation by ID
     */
    suspend fun loadConversation(conversationId: String): Conversation? {
        return try {
            val doc = firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .get()
                .await()

            doc.toObject(Conversation::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading conversation: ${e.message}")
            null
        }
    }

    /**
     * Delete a conversation
     */
    suspend fun deleteConversation(conversationId: String) {
        try {
            firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .delete()
                .await()
            Log.d(TAG, "Deleted conversation: $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting conversation: ${e.message}")
            throw e
        }
    }
}

