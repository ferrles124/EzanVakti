package com.mehmet.ezanvakti

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        val client = LocationServices.getFusedLocationProviderClient(context)

        val location = try {
            getFreshLocation(client)
        } catch (e: Exception) {
            null
        }

        return location?.let { Pair(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(client: com.google.android.gms.location.FusedLocationProviderClient): Location? {
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }
}
