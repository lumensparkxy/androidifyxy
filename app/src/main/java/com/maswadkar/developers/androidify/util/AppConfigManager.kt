package com.maswadkar.developers.androidify.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.maswadkar.developers.androidify.BuildConfig

/**
 * Manages app configuration with Firebase Remote Config as primary source
 * and BuildConfig values as fallback.
 */
object AppConfigManager {

    private const val TAG = "AppConfigManager"

    // Remote Config keys
    private const val KEY_SUPPLIER_WHATSAPP_NUMBER = "supplier_whatsapp_number"

    // Default fallback value (matches local.properties)
    private const val DEFAULT_WHATSAPP_NUMBER = "919403513382"

    // Cache fetch interval (12 hours for production, 1 minute for debug)
    private val FETCH_INTERVAL_SECONDS = if (BuildConfig.DEBUG) 60L else 43200L

    private var isInitialized = false
    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    /**
     * Initialize Remote Config with default values and fetch latest config
     */
    fun initialize() {
        if (isInitialized) return

        try {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            // Set default values
            val defaults = mapOf<String, Any>(
                KEY_SUPPLIER_WHATSAPP_NUMBER to getDefaultWhatsAppNumber()
            )
            remoteConfig.setDefaultsAsync(defaults)

            // Fetch and activate
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Remote Config fetched and activated successfully")
                    } else {
                        Log.w(TAG, "Remote Config fetch failed, using defaults")
                    }
                    isInitialized = true
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config: ${e.message}")
            isInitialized = true // Mark as initialized to prevent retry loops
        }
    }

    /**
     * Get the default WhatsApp number from BuildConfig or hardcoded fallback
     */
    private fun getDefaultWhatsAppNumber(): String {
        return try {
            // Try to get from BuildConfig (set in local.properties)
            BuildConfig.SUPPLIER_WHATSAPP_NUMBER
        } catch (e: Exception) {
            // Fallback if BuildConfig field doesn't exist
            DEFAULT_WHATSAPP_NUMBER
        }
    }

    /**
     * Get the supplier WhatsApp number for product inquiry
     * @return WhatsApp number (without + prefix, e.g., "919403513382")
     */
    fun getSupplierWhatsAppNumber(): String {
        return try {
            val remoteValue = remoteConfig.getString(KEY_SUPPLIER_WHATSAPP_NUMBER)
            if (remoteValue.isNotBlank()) {
                remoteValue
            } else {
                getDefaultWhatsAppNumber()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WhatsApp number from Remote Config: ${e.message}")
            getDefaultWhatsAppNumber()
        }
    }
}
