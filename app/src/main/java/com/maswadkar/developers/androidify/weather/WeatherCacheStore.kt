package com.maswadkar.developers.androidify.weather

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch

private val Context.weatherDataStore by preferencesDataStore(name = "weather_cache")

internal class WeatherCacheStore(private val appContext: Context) {

    companion object {
        private val KEY_FETCHED_AT = longPreferencesKey("fetched_at")
        private val KEY_LAT = doublePreferencesKey("lat")
        private val KEY_LON = doublePreferencesKey("lon")
        private val KEY_LOCATION_LABEL = stringPreferencesKey("location_label")
        private val KEY_FORECAST_JSON = stringPreferencesKey("forecast_json")
    }

    data class CachedForecast(
        val fetchedAtEpochMillis: Long,
        val lat: Double,
        val lon: Double,
        val locationLabel: String,
        val forecastJson: String
    )

    suspend fun read(): CachedForecast? {
        val prefs = appContext.weatherDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()

        val fetchedAt = prefs[KEY_FETCHED_AT] ?: return null
        val lat = prefs[KEY_LAT] ?: return null
        val lon = prefs[KEY_LON] ?: return null
        val label = prefs[KEY_LOCATION_LABEL] ?: return null
        val json = prefs[KEY_FORECAST_JSON] ?: return null

        return CachedForecast(
            fetchedAtEpochMillis = fetchedAt,
            lat = lat,
            lon = lon,
            locationLabel = label,
            forecastJson = json
        )
    }

    suspend fun write(cache: CachedForecast) {
        appContext.weatherDataStore.edit { prefs ->
            prefs[KEY_FETCHED_AT] = cache.fetchedAtEpochMillis
            prefs[KEY_LAT] = cache.lat
            prefs[KEY_LON] = cache.lon
            prefs[KEY_LOCATION_LABEL] = cache.locationLabel
            prefs[KEY_FORECAST_JSON] = cache.forecastJson
        }
    }

    suspend fun clear() {
        appContext.weatherDataStore.edit { it.clear() }
    }
}
