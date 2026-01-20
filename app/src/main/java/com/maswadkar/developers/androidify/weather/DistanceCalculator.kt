package com.maswadkar.developers.androidify.weather

import kotlin.math.*

internal object DistanceCalculator {

    /**
     * Returns distance in kilometers between two points using the Haversine formula.
     */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val earthRadiusKm = 6371.0

        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)

        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)

        val h =
            sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)

        return 2 * earthRadiusKm * asin(min(1.0, sqrt(h)))
    }
}
