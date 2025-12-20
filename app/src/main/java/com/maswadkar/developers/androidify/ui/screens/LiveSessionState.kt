package com.maswadkar.developers.androidify.ui.screens

/**
 * Represents the different states of a Live conversation session
 */
sealed class LiveSessionState {
    /**
     * Session is not active
     */
    data object Idle : LiveSessionState()

    /**
     * Establishing connection to Gemini Live API
     */
    data object Connecting : LiveSessionState()

    /**
     * Session is connected and listening to user
     */
    data object Listening : LiveSessionState()

    /**
     * Model is processing/thinking
     */
    data object Processing : LiveSessionState()

    /**
     * Model is speaking/playing audio response
     */
    data object ModelSpeaking : LiveSessionState()

    /**
     * Session ended normally
     */
    data object Ended : LiveSessionState()

    /**
     * Error occurred during session
     */
    data class Error(val message: String) : LiveSessionState()
}

