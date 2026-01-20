package com.maswadkar.developers.androidify.weather

import retrofit2.http.GET
import retrofit2.http.Query

internal interface WeatherApiService {

    @GET("v1/forecast.json")
    suspend fun getForecast(
        @Query("key") apiKey: String,
        /**
         * Query can be "lat,lon" for coordinates.
         */
        @Query("q") query: String,
        @Query("days") days: Int = 3,
        @Query("aqi") aqi: String = "no",
        @Query("alerts") alerts: String = "no"
    ): WeatherApiForecastResponse
}
