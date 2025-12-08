package com.maswadkar.developers.androidify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {

    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 80

    /**
     * Load and compress an image from URI to a Bitmap with max dimension of 1024px
     */
    fun loadAndCompressBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            // First, decode bounds only to calculate scaling
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return null
            }

            // Calculate sample size for initial downscaling
            val sampleSize = calculateSampleSize(originalWidth, originalHeight, MAX_DIMENSION)

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }

            var bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }

            if (bitmap == null) return null

            // Handle EXIF orientation
            bitmap = handleExifOrientation(context, uri, bitmap)

            // Scale to exact max dimension if needed
            bitmap = scaleToMaxDimension(bitmap, MAX_DIMENSION)

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compress a Bitmap directly (used for camera captures)
     */
    @Suppress("unused")
    fun compressBitmap(bitmap: Bitmap): Bitmap {
        return scaleToMaxDimension(bitmap, MAX_DIMENSION)
    }

    /**
     * Convert Bitmap to ByteArray with JPEG compression
     */
    @Suppress("unused")
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val maxOriginal = max(width, height)

        while (maxOriginal / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxOriginal = max(width, height)

        if (maxOriginal <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOriginal
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return bitmap.scale(newWidth, newHeight, true)
    }

    private fun handleExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    else -> return bitmap
                }

                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } ?: bitmap
        } catch (_: Exception) {
            bitmap
        }
    }
}
