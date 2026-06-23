package com.maswadkar.developers.androidify.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.maswadkar.developers.androidify.util.ImageUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class FieldDiaryRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    companion object {
        private const val TAG = "FieldDiaryRepository"
        private const val JPEG_QUALITY = 80

        @Volatile
        private var instance: FieldDiaryRepository? = null

        fun getInstance(): FieldDiaryRepository {
            return instance ?: synchronized(this) {
                instance ?: FieldDiaryRepository().also { instance = it }
            }
        }
    }

    fun generateEntryId(): String {
        val userId = requireSignedInUserId()
        return firestore.fieldDiaryCollection(userId).document().id
    }

    fun getEntriesFlow(): Flow<List<FieldDiaryEntry>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close(IllegalStateException("Sign in required"))
            return@callbackFlow
        }

        val listener = firestore.fieldDiaryCollection(userId)
            .orderBy("activityDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val entries = snapshot?.documents
                    ?.mapNotNull { document ->
                        try {
                            document.toObject(FieldDiaryEntry::class.java)
                                ?.copy(id = document.id, userId = userId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing diary entry ${document.id}: ${e.message}", e)
                            null
                        }
                    }
                    .orEmpty()

                trySend(entries.sortedNewestFirst())
            }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Stopped listening to field diary entries")
        }
    }

    suspend fun listEntries(): List<FieldDiaryEntry> {
        val userId = requireSignedInUserId()
        val snapshot = firestore.fieldDiaryCollection(userId)
            .orderBy("activityDate", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            try {
                document.toObject(FieldDiaryEntry::class.java)
                    ?.copy(id = document.id, userId = userId)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing diary entry ${document.id}: ${e.message}", e)
                null
            }
        }.sortedNewestFirst()
    }

    suspend fun createEntry(entry: FieldDiaryEntry): String {
        val userId = requireSignedInUserId()
        val entryRef = if (entry.id.isBlank()) {
            firestore.fieldDiaryCollection(userId).document()
        } else {
            firestore.fieldDiaryCollection(userId).document(entry.id.trim())
        }
        val now = Timestamp.now()
        val normalized = entry.preparedForSave(userId, entryRef.id).copy(
            createdAt = entry.createdAt ?: now,
            updatedAt = now
        )

        entryRef.set(normalized.toFirestoreMap(includeCreatedAt = true)).await()
        Log.d(TAG, "Created field diary entry: ${entryRef.id}")
        return entryRef.id
    }

    suspend fun updateEntry(entryId: String, entry: FieldDiaryEntry) {
        val userId = requireSignedInUserId()
        val trimmedEntryId = entryId.trim()
        require(trimmedEntryId.isNotBlank()) { "Entry ID is required" }

        val normalized = entry.preparedForSave(userId, trimmedEntryId).copy(updatedAt = Timestamp.now())

        firestore.fieldDiaryCollection(userId)
            .document(trimmedEntryId)
            .set(normalized.toFirestoreMap(includeCreatedAt = false), SetOptions.merge())
            .await()
        Log.d(TAG, "Updated field diary entry: $trimmedEntryId")
    }

    suspend fun deleteEntry(entryId: String) {
        val userId = requireSignedInUserId()
        val trimmedEntryId = entryId.trim()
        require(trimmedEntryId.isNotBlank()) { "Entry ID is required" }

        firestore.fieldDiaryCollection(userId)
            .document(trimmedEntryId)
            .delete()
            .await()
        Log.d(TAG, "Deleted field diary entry: $trimmedEntryId")
    }

    suspend fun uploadPhoto(context: Context, entryId: String, imageUri: Uri): String {
        val userId = requireSignedInUserId()
        val trimmedEntryId = entryId.trim()
        require(trimmedEntryId.isNotBlank()) { "Entry ID is required before uploading diary photos" }

        val bitmap = ImageUtils.loadAndCompressBitmap(context, imageUri)
            ?: throw IllegalStateException("Failed to load image from URI: $imageUri")
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val imageData = outputStream.toByteArray()
        val filename = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val storagePath = buildFieldDiaryPhotoPathPrefix(userId, trimmedEntryId) + filename

        storage.reference.child(storagePath).putBytes(imageData).await()
        Log.d(TAG, "Uploaded field diary photo: $storagePath")
        return storagePath
    }

    suspend fun deletePhoto(storagePath: String) {
        val userId = requireSignedInUserId()
        val trimmedPath = storagePath.trim()
        require(trimmedPath.startsWith("field_diary/$userId/")) { "Diary photo path does not belong to the signed-in user" }

        storage.reference.child(trimmedPath).delete().await()
        Log.d(TAG, "Deleted field diary photo: $trimmedPath")
    }

    suspend fun resolvePhotoUrl(storagePath: String): String {
        val userId = requireSignedInUserId()
        val trimmedPath = storagePath.trim()
        require(trimmedPath.startsWith("field_diary/$userId/")) { "Diary photo path does not belong to the signed-in user" }

        return storage.reference.child(trimmedPath).downloadUrl.await().toString()
    }

    private fun requireSignedInUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Sign in required")
}

private fun FieldDiaryEntry.preparedForSave(userId: String, entryId: String): FieldDiaryEntry {
    val errors = validationErrorsForSave(userId, entryId)
    require(errors.isEmpty()) {
        errors.joinToString(separator = "; ") { it.message }
    }

    return normalizedForSave(userId, entryId)
}

private fun FieldDiaryEntry.toFirestoreMap(includeCreatedAt: Boolean): Map<String, Any?> {
    val values = linkedMapOf<String, Any?>(
        "id" to id,
        "userId" to userId,
        "activityDate" to activityDate,
        "activityType" to activityType,
        "cropName" to cropName,
        "fieldName" to fieldName,
        "notes" to notes,
        "inputName" to inputName,
        "quantity" to quantity,
        "costAmount" to costAmount,
        "photoPaths" to photoPaths,
        "updatedAt" to updatedAt
    )
    if (includeCreatedAt) {
        values["createdAt"] = createdAt
    }
    return values
}

private fun List<FieldDiaryEntry>.sortedNewestFirst(): List<FieldDiaryEntry> =
    sortedWith(
        compareByDescending<FieldDiaryEntry> { it.activityDate?.seconds ?: Long.MIN_VALUE }
            .thenByDescending { it.activityDate?.nanoseconds ?: Int.MIN_VALUE }
    )
