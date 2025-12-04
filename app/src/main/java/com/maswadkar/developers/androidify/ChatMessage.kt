package com.maswadkar.developers.androidify

data class ChatMessage(
    var text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)