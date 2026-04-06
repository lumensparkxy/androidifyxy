package com.maswadkar.developers.androidify.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

internal const val USERS_COLLECTION = "users"
internal const val SETTINGS_COLLECTION = "settings"
internal const val FARMER_PROFILE_DOC = "farmer_profile"

internal fun FirebaseFirestore.userDocument(userId: String): DocumentReference =
    collection(USERS_COLLECTION).document(userId)

internal fun FirebaseFirestore.farmerProfileDocument(userId: String): DocumentReference =
    userDocument(userId)
        .collection(SETTINGS_COLLECTION)
        .document(FARMER_PROFILE_DOC)