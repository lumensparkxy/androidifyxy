package com.maswadkar.developers.androidify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        private const val MESSAGES_KEY = "chat_messages"
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        savedStateHandle.get<ArrayList<ChatMessage>>(MESSAGES_KEY) ?: emptyList()
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

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

