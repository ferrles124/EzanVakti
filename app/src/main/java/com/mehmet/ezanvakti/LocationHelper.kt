package com.mehmet.ezanvakti

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // Önce taze bir konum iste (GPS/ağ, yüksek doğruluk)
        val fresh = trySuspend {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(15000)
                .build()
            client.getCurrentLocation(request, null)
        }
        if (fresh != null) return Pair(fresh.latitude, fresh.longitude)

        // Taze konum gelmezse son bilinen konumu dene
        val last = trySuspend { client.lastLocation }
        if (last != null) return Pair(last.latitude, last.longitude)

        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun trySuspend(
        call: () -> com.google.android.gms.tasks.Task<android.location.Location>
    ): android.location.Location? {
        return suspendCancellableCoroutine { cont ->
            call()
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}
