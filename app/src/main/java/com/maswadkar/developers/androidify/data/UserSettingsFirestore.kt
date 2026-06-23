package com.maswadkar.developers.androidify.data

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

internal const val USERS_COLLECTION = "users"
internal const val SETTINGS_COLLECTION = "settings"
internal const val FARMER_PROFILE_DOC = "farmer_profile"
internal const val FIELD_DIARY_COLLECTION = "field_diary"

internal fun FirebaseFirestore.userDocument(userId: String): DocumentReference =
    collection(USERS_COLLECTION).document(userId)

internal fun FirebaseFirestore.farmerProfileDocument(userId: String): DocumentReference =
    userDocument(userId)
        .collection(SETTINGS_COLLECTION)
        .document(FARMER_PROFILE_DOC)

internal fun FirebaseFirestore.fieldDiaryCollection(userId: String): CollectionReference =
    userDocument(userId).collection(FIELD_DIARY_COLLECTION)
