package com.maswadkar.developers.androidify

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lightweight test to ensure the new Weather strings are present in the default resources.
 * (The Android resource merge/build will validate translations at build time.)
 */
class WeatherStringsTest {
    @Test
    fun weatherStringsKeysExistInRepo() {
        // If this file compiles, the R references are generated.
        assertTrue(R.string.menu_weather != 0)
        assertTrue(R.string.weather_title != 0)
    }
}
