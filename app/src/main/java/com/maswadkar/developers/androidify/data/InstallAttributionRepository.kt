package com.maswadkar.developers.androidify.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

private const val TAG = "InstallAttributionRepo"
private const val PREFS_NAME = "install_attribution"
private const val KEY_INSTALL_ID = "install_id"
private const val KEY_ATTRIBUTION_CAPTURED = "install_attribution_captured"
private const val KEY_FIRST_OPEN_AT_MS = "first_open_at_ms"
private const val KEY_LAST_LINKED_USER_ID = "last_linked_user_id"
private const val CALLABLE_UPSERT_INSTALL_ATTRIBUTION = "upsertInstallAttribution"
private const val ATTRIBUTION_STATUS_PROMOTER_ATTRIBUTED = "promoter_attributed"
private const val ATTRIBUTION_STATUS_ORGANIC_OR_UNKNOWN = "organic_or_unknown"

internal fun parsePromoterIdFromRawReferrer(rawReferrer: String?): String? {
    val normalized = rawReferrer?.trim().orEmpty()
    if (normalized.isBlank()) return null

    return normalized.split('&')
        .asSequence()
        .mapNotNull { segment ->
            val separatorIndex = segment.indexOf('=')
            val encodedKey = if (separatorIndex >= 0) segment.substring(0, separatorIndex) else segment
            val encodedValue = if (separatorIndex >= 0) segment.substring(separatorIndex + 1) else ""
            val key = decodeReferrerComponent(encodedKey)
            if (key != "pid") {
                null
            } else {
                decodeReferrerComponent(encodedValue).trim().ifBlank { null }
            }
        }
        .firstOrNull()
}

internal fun getAttributionStatus(promoterId: String?): String {
    return if (promoterId.isNullOrBlank()) {
        ATTRIBUTION_STATUS_ORGANIC_OR_UNKNOWN
    } else {
        ATTRIBUTION_STATUS_PROMOTER_ATTRIBUTED
    }
}

private fun decodeReferrerComponent(value: String): String {
    return try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }
}

class InstallAttributionRepository private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: InstallAttributionRepository? = null

        fun getInstance(context: Context): InstallAttributionRepository {
            return instance ?: synchronized(this) {
                instance ?: InstallAttributionRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun getOrCreateInstallId(): String {
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, created).apply()
        return created
    }

    suspend fun captureAttributionIfNeeded() {
        if (prefs.getBoolean(KEY_ATTRIBUTION_CAPTURED, false)) return

        val installId = getOrCreateInstallId()
        val firstOpenAtEpochMillis = getOrCreateFirstOpenAtEpochMillis()
        val rawReferrer = try {
            fetchRawInstallReferrer()
        } catch (e: Exception) {
            Log.w(TAG, "Install Referrer unavailable, storing unknown attribution", e)
            null
        }
        val promoterId = parsePromoterIdFromRawReferrer(rawReferrer)
        val attributionStatus = getAttributionStatus(promoterId)

        try {
            functions
                .getHttpsCallable(CALLABLE_UPSERT_INSTALL_ATTRIBUTION)
                .call(
                    hashMapOf(
                        "installId" to installId,
                        "promoterId" to promoterId,
                        "rawReferrer" to rawReferrer,
                        "attributionStatus" to attributionStatus,
                        "firstOpenAtEpochMillis" to firstOpenAtEpochMillis
                    )
                )
                .await()

            prefs.edit()
                .putBoolean(KEY_ATTRIBUTION_CAPTURED, true)
                .apply()

            auth.currentUser?.uid?.let { userId ->
                prefs.edit().putString(KEY_LAST_LINKED_USER_ID, userId).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist install attribution", e)
        }
    }

    suspend fun linkUserToInstall(userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) return

        captureAttributionIfNeeded()

        if (prefs.getString(KEY_LAST_LINKED_USER_ID, null) == normalizedUserId) return

        try {
            functions
                .getHttpsCallable(CALLABLE_UPSERT_INSTALL_ATTRIBUTION)
                .call(
                    hashMapOf(
                        "installId" to getOrCreateInstallId(),
                        "firstOpenAtEpochMillis" to getOrCreateFirstOpenAtEpochMillis()
                    )
                )
                .await()

            prefs.edit().putString(KEY_LAST_LINKED_USER_ID, normalizedUserId).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link install attribution to user", e)
        }
    }

    private fun getOrCreateFirstOpenAtEpochMillis(): Long {
        val existing = prefs.getLong(KEY_FIRST_OPEN_AT_MS, 0L)
        if (existing > 0L) return existing

        val created = System.currentTimeMillis()
        prefs.edit().putLong(KEY_FIRST_OPEN_AT_MS, created).apply()
        return created
    }

    private suspend fun fetchRawInstallReferrer(): String? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(appContext).build()

        continuation.invokeOnCancellation {
            runCatching { client.endConnection() }
        }

        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                try {
                    val rawReferrer = when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            runCatching { client.installReferrer.installReferrer }
                                .onFailure { Log.w(TAG, "Failed reading install referrer", it) }
                                .getOrNull()
                        }
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE,
                        InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR,
                        InstallReferrerClient.InstallReferrerResponse.PERMISSION_ERROR,
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED -> {
                            Log.w(TAG, "Install Referrer setup finished with code: $responseCode")
                            null
                        }
                        else -> {
                            Log.w(TAG, "Unexpected Install Referrer response code: $responseCode")
                            null
                        }
                    }

                    if (continuation.isActive) {
                        continuation.resume(rawReferrer)
                    }
                } finally {
                    runCatching { client.endConnection() }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
                runCatching { client.endConnection() }
            }
        })
    }
}

