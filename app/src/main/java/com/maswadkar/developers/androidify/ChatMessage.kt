package com.maswadkar.developers.androidify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    var text: String,
    val isUser: Boolean,
    var isLoading: Boolean = false,
    val imageUri: String? = null,  // Local URI string for attached image (temporary, device-specific)
    val imageUrl: String? = null   // Remote Firebase Storage URL (persisted, for history reload)
) : Parcelable
