package com.maswadkar.developers.androidify.weather

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class LocationRepository(private val appContext: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission")
    suspend fun getCoarseLocation(): LatLon? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                cont.resume(location?.let { LatLon(it.latitude, it.longitude) })
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }
}
