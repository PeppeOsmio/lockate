package com.peppeosmio.lockate.service.location

import android.Manifest
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.service.PermissionsService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationService(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val permissionsService: PermissionsService
) {

    @Throws
    private fun checkPermissions() {
        if (permissionsService.isPermissionDenied(Manifest.permission.ACCESS_COARSE_LOCATION) || permissionsService.isPermissionDenied(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            throw NoPermissionException("${Manifest.permission.ACCESS_COARSE_LOCATION} or ${Manifest.permission.ACCESS_FINE_LOCATION}")
        }
        if (permissionsService.isPermissionDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            throw NoPermissionException(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (permissionsService.isPermissionDenied(Manifest.permission.FOREGROUND_SERVICE_LOCATION)) {
                throw NoPermissionException(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getLocationUpdates(): Flow<Location> {
        return callbackFlow {

            checkPermissions()

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10000L
            ).build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (locationResult.lastLocation == null) {
                        return
                    }
                    launch {
                        send(
                            Location(
                                latitude = locationResult.lastLocation!!.latitude,
                                longitude = locationResult.lastLocation!!.longitude
                            )
                        )
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )

            awaitClose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    suspend fun getCurrentLocation(): Location? {
        Log.d("", "Getting current location...")
        checkPermissions()
        try {
            return suspendCancellableCoroutine { cont ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { coordinates ->
                        val location = if (coordinates == null) {
                            null
                        } else {
                            Location(
                                latitude = coordinates.latitude, longitude = coordinates.longitude
                            )
                        }
                        Log.d("", "Got location: $location")
                        cont.resume(
                            location
                        ) { _, _, _ -> }
                    }.addOnFailureListener {
                        Log.d("", "Got location: null")
                        cont.resume(null) { _, _, _ -> }
                    }
            }
        } catch (e: SecurityException) {
            throw NoPermissionException("")
        }
    }
}