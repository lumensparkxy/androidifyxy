package com.maswadkar.developers.androidify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    var text: String,
    val isUser: Boolean,
    var isLoading: Boolean = false,
    val imageUri: String? = null  // URI string for attached image (not persisted to Firestore in v1)
) : Parcelable
