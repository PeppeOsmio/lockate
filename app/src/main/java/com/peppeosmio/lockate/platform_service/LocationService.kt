package com.peppeosmio.lockate.platform_service

import  android.Manifest
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.service.PermissionsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

class LocationService(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val permissionsService: PermissionsService
) {

    private val _coordinatesUpdates = MutableSharedFlow<Coordinates>(
        replay = 0,
        extraBufferCapacity = 1
    )

    @OptIn(ExperimentalAtomicApi::class)
    private val activeCollectors = AtomicInt(0)
    private var locationCallback: LocationCallback? = null

    private fun checkPermissions() {
        if (permissionsService.isPermissionDenied(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            permissionsService.isPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            throw NoPermissionException(
                "${Manifest.permission.ACCESS_COARSE_LOCATION} or ${Manifest.permission.ACCESS_FINE_LOCATION}"
            )
        }
        if (permissionsService.isPermissionDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            throw NoPermissionException(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            permissionsService.isPermissionDenied(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        ) {
            throw NoPermissionException(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
    }

    fun getLocationUpdates(): Flow<Coordinates> = flow {
        onCollectorAdded()
        try {
            emitAll(_coordinatesUpdates)
        } finally {
            onCollectorRemoved()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun onCollectorAdded() {
        if (activeCollectors.incrementAndFetch() == 1) {
            startLocationUpdates()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun onCollectorRemoved() {
        if (activeCollectors.decrementAndFetch() == 0) {
            stopLocationUpdates()
        }
    }

    @Throws(NoPermissionException::class)
    private fun startLocationUpdates() {
        checkPermissions()
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    _coordinatesUpdates.tryEmit(Coordinates(loc.latitude, loc.longitude))
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            throw NoPermissionException("")
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    suspend fun getCurrentLocation(): Coordinates? {
        Log.d("", "Getting current location...")
        checkPermissions()
        return try {
            suspendCancellableCoroutine { cont ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { coordinates ->
                        val location = coordinates?.let {
                            Coordinates(it.latitude, it.longitude)
                        }
                        cont.resume(location) { _, _, _ -> }
                    }
                    .addOnFailureListener {
                        cont.resume(null) { _, _, _ -> }
                    }
            }
        } catch (e: SecurityException) {
            throw NoPermissionException("")
        }
    }
}
