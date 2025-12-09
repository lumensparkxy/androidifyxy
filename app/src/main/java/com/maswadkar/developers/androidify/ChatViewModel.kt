package com.maswadkar.developers.androidify

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.maswadkar.developers.androidify.data.ChatRepository
import com.maswadkar.developers.androidify.data.Conversation
import com.maswadkar.developers.androidify.data.Message
import com.maswadkar.developers.androidify.util.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChatViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

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

    // Reactive user ID flow for auth state changes
    private val _userIdFlow = MutableStateFlow(currentUserId)

    // Flow of user's conversation history - reactive to auth state
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conversationsFlow: StateFlow<List<Conversation>> = _userIdFlow
        .flatMapLatest { userId ->
            if (userId != null) {
                chatRepository.getConversationsFlow(userId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val model = Firebase.ai(backend = GenerativeBackend.vertexAI())
        .generativeModel(
            modelName = AppConstants.AI_MODEL_NAME,
            systemInstruction = content { text(AppConstants.AI_SYSTEM_INSTRUCTION) }
        )

    // Chat instance for multi-turn conversations with context
    private var chat: Chat? = null

    private var currentRequestJob: Job? = null

    // Mutex to prevent concurrent save operations creating duplicate records
    private val saveMutex = Mutex()

    /**
     * Build chat history from current messages for initializing Chat with context.
     * Images are represented as text-only (image data is not persisted in history).
     */
    private fun buildChatHistory(): List<Content> {
        return _messages.value
            .filter { !it.isLoading }
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    // For messages that had images, use the text portion only
                    // (or a placeholder if the original text was blank)
                    val messageText = if (msg.imageUri != null && msg.text == "[Image attached]") {
                        "[User shared an image]"
                    } else {
                        msg.text
                    }
                    text(messageText)
                }
            }
    }

    /**
     * Initialize or reinitialize the chat with current message history
     */
    private fun initializeChatWithHistory() {
        val history = buildChatHistory()
        chat = model.startChat(history = history)
        Log.d(TAG, "Initialized chat with ${history.size} history entries")
    }

    init {
        // Set up auth state listener to update userId flow
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            _userIdFlow.value = auth.currentUser?.uid
            Log.d(TAG, "Auth state changed, userId: ${auth.currentUser?.uid}")
        }

        // Clean up any loading messages from previous session (process death scenario)
        val cleanedMessages = _messages.value.map { msg ->
            if (msg.isLoading) msg.copy(text = AppConstants.REQUEST_INTERRUPTED_MESSAGE, isLoading = false)
            else msg
        }
        if (cleanedMessages != _messages.value) {
            updateMessages(cleanedMessages)
        }
    }

    /**
     * Refresh the user ID flow - call this when auth state may have changed
     */
    fun refreshUserState() {
        _userIdFlow.value = currentUserId
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
        // Reset chat for fresh context
        chat = model.startChat()
        Log.d(TAG, "Started new conversation")
    }

    /**
     * Clear current conversation and start fresh (used after deleting current conversation)
     */
    private fun clearCurrentConversation() {
        currentConversationId = null
        savedStateHandle[CONVERSATION_ID_KEY] = null
        updateMessages(emptyList())
        // Reset chat for fresh context
        chat = model.startChat()
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

                    // Initialize chat with loaded history for context
                    initializeChatWithHistory()

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
     * Save current conversation to Firestore (async version for regular use)
     */
    fun saveCurrentConversation() {
        val userId = currentUserId ?: return
        // Capture values NOW to prevent race conditions with startNewConversation()
        val conversationIdToSave = currentConversationId
        val messagesToSave = _messages.value.toList()

        // Don't save empty conversations or conversations with only loading messages
        if (messagesToSave.isEmpty() || messagesToSave.all { it.isLoading }) return

        viewModelScope.launch {
            saveConversationInternal(userId, conversationIdToSave, messagesToSave)
        }
    }

    /**
     * Save current conversation to Firestore synchronously (for app exit scenarios)
     * Call this from a coroutine scope that won't be cancelled on activity destruction
     */
    suspend fun saveCurrentConversationSync() {
        val userId = currentUserId ?: return
        // Capture values NOW to prevent race conditions
        val conversationIdToSave = currentConversationId
        val messagesToSave = _messages.value.toList()

        // Don't save empty conversations or conversations with only loading messages
        if (messagesToSave.isEmpty() || messagesToSave.all { it.isLoading }) return

        saveConversationInternal(userId, conversationIdToSave, messagesToSave)
    }

    /**
     * Internal save logic shared by async and sync versions.
     * Uses NonCancellable to ensure save completes even when coroutine is cancelled.
     *
     * @param userId The user ID to save under
     * @param conversationIdToSave The conversation ID captured at save request time (null for new)
     * @param messagesToSave The messages captured at save request time
     */
    private suspend fun saveConversationInternal(
        userId: String,
        conversationIdToSave: String?,
        messagesToSave: List<ChatMessage>
    ) {
        withContext(NonCancellable) {
            saveMutex.withLock {
                // Check if messages are valid
                if (messagesToSave.isEmpty() || messagesToSave.all { it.isLoading }) return@withLock

                try {
                    // Convert ChatMessages to Firestore Messages
                    val firestoreMessages = messagesToSave.filter { !it.isLoading }.map { msg ->
                        Message(
                            text = msg.text,
                            isUser = msg.isUser,
                            timestamp = Timestamp.now()
                        )
                    }

                    // Generate title from first user message
                    val title = messagesToSave.firstOrNull { it.isUser }?.let {
                        Conversation.generateTitle(it.text)
                    } ?: "New Conversation"

                    val conversation = Conversation(
                        id = conversationIdToSave ?: "",  // Use CAPTURED ID, not currentConversationId
                        userId = userId,
                        title = title,
                        messages = firestoreMessages
                    )

                    val savedId = chatRepository.saveConversation(conversation)

                    // Only update currentConversationId if:
                    // 1. This was a new conversation (conversationIdToSave was null), AND
                    // 2. The current conversation ID is STILL null (user hasn't started another new one)
                    if (conversationIdToSave == null && currentConversationId == null) {
                        currentConversationId = savedId
                        savedStateHandle[CONVERSATION_ID_KEY] = savedId
                        Log.d(TAG, "Set new conversation ID: $savedId")
                    }

                    Log.d(TAG, "Saved conversation: $savedId (was new: ${conversationIdToSave == null})")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving conversation: ${e.message}")
                }
            }
        }
    }

    fun sendMessage(userText: String, imageUri: Uri? = null) {
        if (userText.isBlank() && imageUri == null) return

        // Add user message (with image URI if present)
        val userMessage = ChatMessage(
            text = userText.ifBlank { "[Image attached]" },
            isUser = true,
            imageUri = imageUri?.toString()
        )
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
                // Initialize chat if null (e.g., after process death or first message)
                if (chat == null) {
                    // Build history from messages before the current user message
                    val historyMessages = _messages.value.dropLast(2) // Exclude current user msg and loading msg
                    val history = historyMessages
                        .filter { !it.isLoading }
                        .map { msg ->
                            content(role = if (msg.isUser) "user" else "model") {
                                val messageText = if (msg.imageUri != null && msg.text == "[Image attached]") {
                                    "[User shared an image]"
                                } else {
                                    msg.text
                                }
                                text(messageText)
                            }
                        }
                    chat = model.startChat(history = history)
                    Log.d(TAG, "Initialized chat with ${history.size} history entries (lazy init)")
                }

                // Build content with image if present
                val response = if (imageUri != null) {
                    // Load and compress the image
                    val bitmap = ImageUtils.loadAndCompressBitmap(
                        getApplication<Application>().applicationContext,
                        imageUri
                    )

                    if (bitmap != null) {
                        // Send message with image and text using chat
                        chat!!.sendMessage(
                            content {
                                image(bitmap)
                                if (userText.isNotBlank()) {
                                    text(userText)
                                }
                            }
                        )
                    } else {
                        // Fallback to text-only if image loading fails
                        chat!!.sendMessage(userText.ifBlank { "Please describe what you see." })
                    }
                } else {
                    // Text-only message using chat
                    chat!!.sendMessage(userText)
                }

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
