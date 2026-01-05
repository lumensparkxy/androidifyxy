package com.maswadkar.developers.androidify.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.maswadkar.developers.androidify.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ViewModel for managing Gemini Live API voice conversations.
 * Uses the built-in startAudioConversation() method which handles
 * audio recording, streaming, and playback automatically.
 */
@OptIn(PublicPreviewAPI::class)
class LiveConversationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LiveConversationVM"
        // Use the native audio model for better audio conversation support
        private const val LIVE_MODEL_NAME = "gemini-live-2.5-flash-native-audio"
    }

    private val _sessionState = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private var liveSession: LiveSession? = null

    // Get device locale for language fallback hint
    private val deviceLocale: String = Locale.getDefault().toLanguageTag()

    // Configure the Live Model with audio input/output
    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.vertexAI())
            .liveModel(
                modelName = LIVE_MODEL_NAME,
                generationConfig = liveGenerationConfig {
                    speechConfig = SpeechConfig(voice = Voice("Kore"))
                    responseModality = ResponseModality.AUDIO
                },
                systemInstruction = content { text(AppConstants.getSystemInstruction(deviceLocale)) }
            )
    }

    /**
     * Check if we have audio recording permission
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start a live conversation session
     */
    @SuppressLint("MissingPermission")
    fun startSession() {
        if (_sessionState.value != LiveSessionState.Idle &&
            _sessionState.value !is LiveSessionState.Error &&
            _sessionState.value != LiveSessionState.Ended) {
            Log.w(TAG, "Session already active, state: ${_sessionState.value}")
            return
        }

        viewModelScope.launch {
            try {
                _sessionState.value = LiveSessionState.Connecting
                Log.d(TAG, "Connecting to Gemini Live API...")

                // Connect to the Live API
                withContext(Dispatchers.IO) {
                    liveSession = liveModel.connect()
                }
                Log.d(TAG, "Connected to Gemini Live API")

                // Start the audio conversation - this handles everything automatically:
                // - Audio recording from microphone
                // - Streaming audio to the API
                // - Receiving and playing audio responses
                _sessionState.value = LiveSessionState.Listening
                Log.d(TAG, "Starting audio conversation...")

                // startAudioConversation() is a suspending function that runs
                // until the conversation ends. Launch it in a separate coroutine
                // so we don't block the current one.
                launch(Dispatchers.IO) {
                    try {
                        liveSession?.startAudioConversation()
                        Log.d(TAG, "Audio conversation ended normally")
                    } catch (_: CancellationException) {
                        Log.d(TAG, "Audio conversation cancelled")
                    } catch (e: Exception) {
                        Log.e(TAG, "Audio conversation error: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            _sessionState.value = LiveSessionState.Error(e.message ?: "Conversation error")
                        }
                    }
                }

                Log.d(TAG, "Audio conversation started")

            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session: ${e.message}", e)
                _sessionState.value = LiveSessionState.Error(e.message ?: "Failed to connect")
                cleanup()
            }
        }
    }

    /**
     * End the current session
     */
    fun endSession() {
        Log.d(TAG, "Ending session...")
        _sessionState.value = LiveSessionState.Ended
        viewModelScope.launch {
            cleanup()
        }
    }

    /**
     * Clean up all resources
     */
    private suspend fun cleanup() {
        withContext(Dispatchers.IO) {
            try {
                liveSession?.stopAudioConversation()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio conversation: ${e.message}")
            }

            try {
                liveSession?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing session: ${e.message}")
            }
            liveSession = null

            Log.d(TAG, "Cleanup complete")
        }
    }

    /**
     * Reset to idle state (for restarting)
     */
    fun resetState() {
        _sessionState.value = LiveSessionState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            cleanup()
        }
    }
}

