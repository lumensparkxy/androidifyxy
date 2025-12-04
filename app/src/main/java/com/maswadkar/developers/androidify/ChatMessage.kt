package com.maswadkar.developers.androidify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    var text: String,
    val isUser: Boolean,
    var isLoading: Boolean = false
) : Parcelable
