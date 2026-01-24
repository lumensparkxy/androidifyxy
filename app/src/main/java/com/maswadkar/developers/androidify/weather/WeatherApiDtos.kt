package com.maswadkar.developers.androidify.weather

import com.google.gson.annotations.SerializedName

internal data class WeatherApiForecastResponse(
    @field:SerializedName("location") val location: LocationDto,
    @field:SerializedName("forecast") val forecast: ForecastDto
)

internal data class LocationDto(
    @field:SerializedName("name") val name: String,
    @field:SerializedName("region") val region: String?,
    @field:SerializedName("country") val country: String?
)

internal data class ForecastDto(
    @field:SerializedName("forecastday") val forecastDays: List<ForecastDayDto>
)

internal data class ForecastDayDto(
    @field:SerializedName("date_epoch") val dateEpochSeconds: Long,
    @field:SerializedName("day") val day: DayDto,
    @field:SerializedName("astro") val astro: AstroDto?
)

internal data class DayDto(
    @field:SerializedName("mintemp_c") val minTempC: Double,
    @field:SerializedName("maxtemp_c") val maxTempC: Double,
    @field:SerializedName("condition") val condition: ConditionDto
)

internal data class ConditionDto(
    @field:SerializedName("text") val text: String,
    @field:SerializedName("icon") val icon: String?
)

internal data class AstroDto(
    @field:SerializedName("sunrise") val sunrise: String?,
    @field:SerializedName("sunset") val sunset: String?,
    @field:SerializedName("moon_phase") val moonPhase: String?
)
