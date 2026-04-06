package com.maswadkar.developers.androidify.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

private const val AGENT_CHAT_PROXY_FUNCTION = "agentChatProxy"

class AgenticChatRepository {

    companion object {
        @Volatile
        private var instance: AgenticChatRepository? = null

        fun getInstance(): AgenticChatRepository {
            return instance ?: synchronized(this) {
                instance ?: AgenticChatRepository().also { instance = it }
            }
        }
    }

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")

    suspend fun sendMessage(request: AgenticChatRequest): AgenticChatResponse {
        val payload = hashMapOf(
            "conversationId" to request.conversationId,
            "message" to request.message,
            "locale" to request.locale,
            "imageUrl" to request.imageUrl,
            "recentMessages" to request.recentMessages.map { message ->
                hashMapOf(
                    "role" to message.role,
                    "text" to message.text,
                    "imageUrl" to message.imageUrl
                )
            }
        )

        val response = functions
            .getHttpsCallable(AGENT_CHAT_PROXY_FUNCTION)
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = response.data as? Map<String, Any?>
            ?: throw IllegalStateException("Unexpected response from agent chat proxy")

        val text = data["text"] as? String
            ?: throw IllegalStateException("Missing response text from agent chat proxy")

        @Suppress("UNCHECKED_CAST")
        val metadataMap = data["metadata"] as? Map<String, Any?> ?: emptyMap()

        return AgenticChatResponse(
            text = text,
            metadata = AgenticChatMetadata(
                leadCreated = metadataMap["leadCreated"] as? Boolean ?: false,
                requestNumber = metadataMap["requestNumber"] as? String,
                citedDocumentIds = (metadataMap["citedDocumentIds"] as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty(),
                traceId = metadataMap["traceId"] as? String,
                askedClarification = metadataMap["askedClarification"] as? Boolean ?: false
            )
        )
    }
}

data class AgenticChatRequest(
    val conversationId: String,
    val message: String,
    val locale: String,
    val imageUrl: String? = null,
    val recentMessages: List<AgenticChatHistoryMessage> = emptyList()
)

data class AgenticChatHistoryMessage(
    val role: String,
    val text: String,
    val imageUrl: String? = null
)

data class AgenticChatResponse(
    val text: String,
    val metadata: AgenticChatMetadata = AgenticChatMetadata()
)

data class AgenticChatMetadata(
    val leadCreated: Boolean = false,
    val requestNumber: String? = null,
    val citedDocumentIds: List<String> = emptyList(),
    val traceId: String? = null,
    val askedClarification: Boolean = false
)
