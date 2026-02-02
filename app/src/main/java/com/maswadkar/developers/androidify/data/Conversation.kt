package com.maswadkar.developers.androidify.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a single message in a conversation
 */
data class Message(
    val text: String = "",
    @get:PropertyName("isUser") @set:PropertyName("isUser")
    var isUser: Boolean = true,
    val timestamp: Timestamp = Timestamp.now(),
    val imageUrl: String? = null
) {
    // No-arg constructor for Firestore
    constructor() : this("", true, Timestamp.now(), null)
}

/**
 * Represents a conversation with all its messages
 */
data class Conversation(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val messages: List<Message> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    // No-arg constructor for Firestore
    constructor() : this("", "", "", emptyList(), null, null)

    companion object {
        private const val MAX_TITLE_LENGTH = 50

        /**
         * Generate a title from the first user message
         */
        fun generateTitle(firstMessage: String): String {
            val trimmed = firstMessage.trim()
            return if (trimmed.length > MAX_TITLE_LENGTH) {
                trimmed.take(MAX_TITLE_LENGTH) + "..."
            } else {
                trimmed
            }
        }
    }
}

