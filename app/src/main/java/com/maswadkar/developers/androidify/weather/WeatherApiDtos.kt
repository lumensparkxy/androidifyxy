package com.maswadkar.developers.androidify.weather

import com.squareup.moshi.Json

internal data class WeatherApiForecastResponse(
    @Json(name = "location") val location: LocationDto,
    @Json(name = "forecast") val forecast: ForecastDto
)

internal data class LocationDto(
    @Json(name = "name") val name: String,
    @Json(name = "region") val region: String?,
    @Json(name = "country") val country: String?
)

internal data class ForecastDto(
    @Json(name = "forecastday") val forecastDays: List<ForecastDayDto>
)

internal data class ForecastDayDto(
    @Json(name = "date_epoch") val dateEpochSeconds: Long,
    @Json(name = "day") val day: DayDto,
    @Json(name = "astro") val astro: AstroDto?
)

internal data class DayDto(
    @Json(name = "mintemp_c") val minTempC: Double,
    @Json(name = "maxtemp_c") val maxTempC: Double,
    @Json(name = "condition") val condition: ConditionDto
)

internal data class ConditionDto(
    @Json(name = "text") val text: String,
    @Json(name = "icon") val icon: String?
)

internal data class AstroDto(
    @Json(name = "sunrise") val sunrise: String?,
    @Json(name = "sunset") val sunset: String?,
    @Json(name = "moon_phase") val moonPhase: String?
)
