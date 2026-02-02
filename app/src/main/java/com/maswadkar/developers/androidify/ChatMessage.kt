package com.maswadkar.developers.androidify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false,
    val imageUri: String? = null,  // Local URI string for attached image (temporary, device-specific)
    val imageUrl: String? = null   // Remote Firebase Storage URL (persisted, for history reload)
) : Parcelable
