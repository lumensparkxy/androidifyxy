package com.maswadkar.developers.androidify.weather

import android.content.Context
import com.maswadkar.developers.androidify.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class WeatherRepository private constructor(appContext: Context) {

    companion object {
        private const val TTL_MILLIS = 3L * 60L * 60L * 1000L // 3 hours
        private const val INVALIDATE_DISTANCE_KM = 50.0

        @Volatile private var instance: WeatherRepository? = null

        fun getInstance(appContext: Context): WeatherRepository {
            return instance ?: synchronized(this) {
                instance ?: WeatherRepository(appContext.applicationContext).also { instance = it }
            }
        }
    }

    private val context = appContext.applicationContext
    private val cache = WeatherCacheStore(context)
    private val locationRepo = LocationRepository(context)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(WeatherApiForecastResponse::class.java)

    private val api = WeatherApiClient.createService(isDebug = BuildConfig.DEBUG)

    suspend fun get3DayForecast(forceRefresh: Boolean = false): WeatherForecast {
        val now = System.currentTimeMillis()
        val currentLatLon = locationRepo.getCoarseLocation()
            ?: throw IllegalStateException("Location unavailable")

        // 1) cache check
        if (!forceRefresh) {
            val cached = cache.read()
            if (cached != null) {
                val ageOk = now - cached.fetchedAtEpochMillis <= TTL_MILLIS
                val distKm = DistanceCalculator.distanceKm(
                    LatLon(cached.lat, cached.lon),
                    currentLatLon
                )
                val distanceOk = distKm <= INVALIDATE_DISTANCE_KM

                if (ageOk && distanceOk) {
                    val parsed = responseAdapter.fromJson(cached.forecastJson)
                        ?: throw IllegalStateException("Cached data unreadable")

                    return parsed.toWeatherForecast(
                        fetchedAtEpochMillis = cached.fetchedAtEpochMillis,
                        isFromCache = true
                    )
                }
            }
        }

        // 2) fetch
        val apiKey = BuildConfig.WEATHER_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("Missing WeatherAPI key")
        }

        val response = api.getForecast(
            apiKey = apiKey,
            query = "${currentLatLon.lat},${currentLatLon.lon}",
            days = 3
        )

        val json = responseAdapter.toJson(response)
        cache.write(
            WeatherCacheStore.CachedForecast(
                fetchedAtEpochMillis = now,
                lat = currentLatLon.lat,
                lon = currentLatLon.lon,
                locationLabel = response.location.toLabel(),
                forecastJson = json
            )
        )

        return response.toWeatherForecast(
            fetchedAtEpochMillis = now,
            isFromCache = false
        )
    }
}

private fun WeatherApiForecastResponse.toWeatherForecast(
    fetchedAtEpochMillis: Long,
    isFromCache: Boolean
): WeatherForecast {
    val firstAstro = forecast.forecastDays.firstOrNull()?.astro

    return WeatherForecast(
        locationLabel = location.toLabel(),
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        days = forecast.forecastDays.take(3).map { it.toDay() },
        isFromCache = isFromCache,
        astro = firstAstro?.toAstro()
    )
}

private fun LocationDto.toLabel(): String {
    val parts = listOfNotNull(
        name.takeIf { it.isNotBlank() },
        region?.takeIf { it.isNotBlank() }
    )
    return parts.joinToString(", ")
}

private fun ForecastDayDto.toDay(): WeatherForecastDay {
    val iconUrl = day.condition.icon?.let {
        // WeatherAPI sometimes returns "//cdn...".
        if (it.startsWith("//")) "https:$it" else it
    }
    return WeatherForecastDay(
        dateEpochSeconds = dateEpochSeconds,
        minTempC = day.minTempC,
        maxTempC = day.maxTempC,
        conditionText = day.condition.text,
        conditionIconUrl = iconUrl
    )
}

private fun AstroDto.toAstro(): WeatherAstro {
    return WeatherAstro(
        sunrise = sunrise,
        sunset = sunset,
        moonPhase = moonPhase
    )
}
