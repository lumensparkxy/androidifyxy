package com.maswadkar.developers.androidify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
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
        private const val MAX_MESSAGES = 100
    }

    private val funnyLoadingMessages = listOf(
        "Thinking... 🤔",
        "Consulting the matrix... 🐇",
        "Reticulating splines... ⚙️",
        "Asking the squirrels... 🐿️",
        "Decoding the cosmos... 🌌",
        "Brewing some coffee... ☕",
        "Waking up the hamsters... 🐹",
        "Connecting to the neural net... 🧠",
        "Looking up the answer in a really big book... 📖",
        "Asking the magic 8-ball... 🎱"
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        savedStateHandle.get<ArrayList<ChatMessage>>(MESSAGES_KEY) ?: emptyList()
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val model = Firebase.ai(backend = GenerativeBackend.vertexAI())
        .generativeModel("gemini-2.5-flash")

    private var currentRequestJob: Job? = null

    init {
        // Clean up any loading messages from previous session (process death scenario)
        val cleanedMessages = _messages.value.map { msg ->
            if (msg.isLoading) msg.copy(text = "Request interrupted. Please try again.", isLoading = false)
            else msg
        }
        if (cleanedMessages != _messages.value) {
            updateMessages(cleanedMessages)
        }
    }

    private fun updateMessages(newMessages: List<ChatMessage>) {
        // Limit messages to prevent memory issues
        val limitedMessages = if (newMessages.size > MAX_MESSAGES) {
            newMessages.takeLast(MAX_MESSAGES)
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
        val loadingMessage = ChatMessage(funnyLoadingMessages.first(), isUser = false, isLoading = true)
        currentMessages.add(loadingMessage)
        updateMessages(currentMessages)

        val loadingIndex = _messages.value.size - 1

        // Cancel any existing request
        currentRequestJob?.cancel()

        currentRequestJob = viewModelScope.launch {
            val animationJob = launch {
                while (isActive) {
                    delay(5000)
                    val updatedMessages = _messages.value.toMutableList()
                    if (loadingIndex < updatedMessages.size && updatedMessages[loadingIndex].isLoading) {
                        updatedMessages[loadingIndex] = updatedMessages[loadingIndex].copy(
                            text = funnyLoadingMessages.random()
                        )
                        updateMessages(updatedMessages)
                    }
                }
            }

            try {
                val response = model.generateContent(userText)
                val modelText = response.text ?: "No response"

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

