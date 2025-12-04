package com.maswadkar.developers.androidify

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.ChatRepository
import com.maswadkar.developers.androidify.data.Conversation
import com.maswadkar.developers.androidify.data.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MESSAGES_KEY = "chat_messages"
        private const val CONVERSATION_ID_KEY = "conversation_id"
    }

    private val chatRepository = ChatRepository.getInstance()
    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        savedStateHandle.get<ArrayList<ChatMessage>>(MESSAGES_KEY) ?: emptyList()
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Current conversation ID (null for new conversations)
    private var currentConversationId: String? = savedStateHandle.get<String>(CONVERSATION_ID_KEY)

    // Flow of user's conversation history
    val conversationsFlow: StateFlow<List<Conversation>> = currentUserId?.let { userId ->
        chatRepository.getConversationsFlow(userId)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    } ?: MutableStateFlow(emptyList())

    private val model = Firebase.ai(backend = GenerativeBackend.vertexAI())
        .generativeModel(
            modelName = AppConstants.AI_MODEL_NAME,
            systemInstruction = content { text(AppConstants.AI_SYSTEM_INSTRUCTION) }
        )

    private var currentRequestJob: Job? = null

    init {
        // Clean up any loading messages from previous session (process death scenario)
        val cleanedMessages = _messages.value.map { msg ->
            if (msg.isLoading) msg.copy(text = AppConstants.REQUEST_INTERRUPTED_MESSAGE, isLoading = false)
            else msg
        }
        if (cleanedMessages != _messages.value) {
            updateMessages(cleanedMessages)
        }
    }

    private fun updateMessages(newMessages: List<ChatMessage>) {
        // Limit messages to prevent memory issues
        val limitedMessages = if (newMessages.size > AppConstants.MAX_MESSAGES) {
            newMessages.takeLast(AppConstants.MAX_MESSAGES)
        } else {
            newMessages
        }
        _messages.value = limitedMessages
        savedStateHandle[MESSAGES_KEY] = ArrayList(limitedMessages)
    }

    /**
     * Start a new conversation (saves current and clears messages)
     */
    fun startNewConversation() {
        saveCurrentConversation()
        currentConversationId = null
        savedStateHandle[CONVERSATION_ID_KEY] = null
        updateMessages(emptyList())
        Log.d(TAG, "Started new conversation")
    }

    /**
     * Clear current conversation and start fresh (used after deleting current conversation)
     */
    private fun clearCurrentConversation() {
        currentConversationId = null
        savedStateHandle[CONVERSATION_ID_KEY] = null
        updateMessages(emptyList())
        Log.d(TAG, "Cleared current conversation")
    }

    /**
     * Load an existing conversation
     */
    fun loadConversation(conversationId: String) {
        // Save current conversation before loading another
        saveCurrentConversation()

        viewModelScope.launch {
            try {
                val conversation = chatRepository.loadConversation(conversationId)
                if (conversation != null) {
                    currentConversationId = conversationId
                    savedStateHandle[CONVERSATION_ID_KEY] = conversationId

                    // Convert Firestore messages to ChatMessage
                    val chatMessages = conversation.messages.map { msg ->
                        ChatMessage(msg.text, msg.isUser, isLoading = false)
                    }
                    updateMessages(chatMessages)
                    Log.d(TAG, "Loaded conversation: $conversationId with ${chatMessages.size} messages")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading conversation: ${e.message}")
            }
        }
    }

    /**
     * Delete a conversation
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteConversation(conversationId)
                // If we deleted the current conversation, clear it
                if (conversationId == currentConversationId) {
                    clearCurrentConversation()
                }
                Log.d(TAG, "Deleted conversation: $conversationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting conversation: ${e.message}")
            }
        }
    }

    /**
     * Save current conversation to Firestore (called when app goes to background)
     */
    fun saveCurrentConversation() {
        val userId = currentUserId ?: return
        val currentMessages = _messages.value

        // Don't save empty conversations or conversations with only loading messages
        if (currentMessages.isEmpty() || currentMessages.all { it.isLoading }) return

        viewModelScope.launch {
            try {
                // Convert ChatMessages to Firestore Messages
                val firestoreMessages = currentMessages.filter { !it.isLoading }.map { msg ->
                    Message(
                        text = msg.text,
                        isUser = msg.isUser,
                        timestamp = Timestamp.now()
                    )
                }

                // Generate title from first user message
                val title = currentMessages.firstOrNull { it.isUser }?.let {
                    Conversation.generateTitle(it.text)
                } ?: "New Conversation"

                val conversation = Conversation(
                    id = currentConversationId ?: "",
                    userId = userId,
                    title = title,
                    messages = firestoreMessages
                )

                val savedId = chatRepository.saveConversation(conversation)

                // Update current conversation ID if this was a new conversation
                if (currentConversationId == null) {
                    currentConversationId = savedId
                    savedStateHandle[CONVERSATION_ID_KEY] = savedId
                }

                Log.d(TAG, "Saved conversation: $savedId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving conversation: ${e.message}")
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        // Add user message
        val userMessage = ChatMessage(userText, isUser = true)
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(userMessage)

        // Add loading message
        val loadingMessage = ChatMessage(AppConstants.LOADING_MESSAGES.first(), isUser = false, isLoading = true)
        currentMessages.add(loadingMessage)
        updateMessages(currentMessages)

        val loadingIndex = _messages.value.size - 1

        // Cancel any existing request
        currentRequestJob?.cancel()

        currentRequestJob = viewModelScope.launch {
            val animationJob = launch {
                while (isActive) {
                    delay(AppConstants.LOADING_MESSAGE_INTERVAL_MS)
                    val updatedMessages = _messages.value.toMutableList()
                    if (loadingIndex < updatedMessages.size && updatedMessages[loadingIndex].isLoading) {
                        updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                            text = AppConstants.LOADING_MESSAGES.random()
                        )
                        updateMessages(updatedMessages)
                    }
                }
            }

            try {
                val response = model.generateContent(userText)
                val modelText = response.text ?: AppConstants.NO_RESPONSE_MESSAGE

                animationJob.cancel()

                // Update loading message with real response
                val updatedMessages = _messages.value.toMutableList()
                if (loadingIndex < updatedMessages.size) {
                    updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                        text = modelText,
                        isLoading = false
                    )
                    updateMessages(updatedMessages)

                    // Save to Firestore after successful response
                    saveCurrentConversation()
                }
            } catch (e: Exception) {
                animationJob.cancel()
                val updatedMessages = _messages.value.toMutableList()
                if (loadingIndex < updatedMessages.size) {
                    updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                        text = "Error: ${e.message}",
                        isLoading = false
                    )
                    updateMessages(updatedMessages)
                }
            }
        }
    }
}
