package com.maswadkar.developers.androidify.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.maswadkar.developers.androidify.util.ImageUtils
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Repository for uploading user images to Firebase Storage.
 * Images are stored permanently for future model training purposes.
 */
class ImageStorageRepository {

    companion object {
        private const val TAG = "ImageStorageRepository"
        private const val USER_IMAGES_PATH = "user_images"
        private const val JPEG_QUALITY = 80

        @Volatile
        private var instance: ImageStorageRepository? = null

        fun getInstance(): ImageStorageRepository {
            return instance ?: synchronized(this) {
                instance ?: ImageStorageRepository().also { instance = it }
            }
        }
    }

    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    /**
     * Upload an image to Firebase Storage.
     *
     * @param context Application context for loading image
     * @param userId The authenticated user's ID
     * @param conversationId The conversation ID (pre-generated UUID for new conversations)
     * @param imageUri Local URI of the image to upload
     * @return Download URL of the uploaded image
     * @throws Exception if upload fails
     */
    suspend fun uploadImage(
        context: Context,
        userId: String,
        conversationId: String,
        imageUri: Uri
    ): String {
        return try {
            Log.d(TAG, "Starting image upload for user: $userId, conversation: $conversationId")

            // Load and compress the image using existing ImageUtils (1024px max, optimized)
            val bitmap = ImageUtils.loadAndCompressBitmap(context, imageUri)
                ?: throw IllegalStateException("Failed to load image from URI: $imageUri")

            // Convert bitmap to byte array
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val imageData = outputStream.toByteArray()

            Log.d(TAG, "Compressed image size: ${imageData.size} bytes")

            // Generate unique filename: timestamp_uuid.jpg
            val timestamp = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString().take(8)
            val filename = "${timestamp}_${uuid}.jpg"

            // Storage path: user_images/{userId}/{conversationId}/{filename}
            val storagePath = "$USER_IMAGES_PATH/$userId/$conversationId/$filename"
            val storageRef = storage.reference.child(storagePath)

            Log.d(TAG, "Uploading to path: $storagePath")

            // Upload the image bytes
            storageRef.putBytes(imageData).await()

            // Get the download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            Log.d(TAG, "Upload successful. Download URL: $downloadUrl")

            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image: ${e.message}", e)
            throw e
        }
    }

    /**
     * Generate a new conversation ID (UUID) for use before first save.
     * This allows uploading images to a consistent path even for new conversations.
     */
    fun generateConversationId(): String {
        return UUID.randomUUID().toString()
    }
}
