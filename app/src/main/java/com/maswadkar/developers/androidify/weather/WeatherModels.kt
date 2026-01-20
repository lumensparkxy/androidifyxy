package com.maswadkar.developers.androidify.weather

/**
 * UI-facing forecast model.
 */
data class WeatherForecast(
    val locationLabel: String,
    val fetchedAtEpochMillis: Long,
    val days: List<WeatherForecastDay>,
    val isFromCache: Boolean,
    val astro: WeatherAstro?
)

data class WeatherForecastDay(
    val dateEpochSeconds: Long,
    val minTempC: Double,
    val maxTempC: Double,
    val conditionText: String,
    val conditionIconUrl: String?
)

data class LatLon(
    val lat: Double,
    val lon: Double
)

/**
 * Astro info for the selected day (we'll use the first forecast day).
 */
data class WeatherAstro(
    val sunrise: String?,
    val sunset: String?,
    val moonPhase: String?
)
