package com.peppeosmio.lockate.platform_service

import  android.Manifest
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.service.ConfigSettings
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.dataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

class LocationService(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val permissionsService: PermissionsService,
) {

    private val _coordinatesUpdates = MutableSharedFlow<Coordinates>(
        replay = 0, extraBufferCapacity = 1
    )

    @OptIn(ExperimentalAtomicApi::class)
    private val activeCollectors = AtomicInt(0)
    private var locationCallback: LocationCallback? = null

    private fun checkPermissions() {
        if (permissionsService.isPermissionDenied(Manifest.permission.ACCESS_COARSE_LOCATION) || permissionsService.isPermissionDenied(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            throw NoPermissionException(
                "${Manifest.permission.ACCESS_COARSE_LOCATION} or ${Manifest.permission.ACCESS_FINE_LOCATION}"
            )
        }
        if (permissionsService.isPermissionDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            throw NoPermissionException(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && permissionsService.isPermissionDenied(
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
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
    private suspend fun onCollectorAdded() {
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

    private suspend fun checkLocationEnabled(): Unit = suspendCancellableCoroutine { cont ->
        val builder = LocationSettingsRequest.Builder()
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            if (it == null) {
                cont.resumeWithException(LocationDisabledException())
            } else {
                val isLocationUsable = it.locationSettingsStates?.isLocationUsable
                if(isLocationUsable == null || !isLocationUsable) {
                    cont.resumeWithException(LocationDisabledException())
                } else {
                    cont.resume(Unit) { _, _, _ -> }
                }
            }
        }
        task.addOnFailureListener { e ->
            cont.resumeWithException(e)
        }
    }

    @Throws(NoPermissionException::class)
    private suspend fun startLocationUpdates() {
        checkPermissions()
        checkLocationEnabled()
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10_000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val coordinates = Coordinates(loc.latitude, loc.longitude)
                    _coordinatesUpdates.tryEmit(coordinates)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback!!, Looper.getMainLooper()
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
        Log.i("", "Getting current location")
        checkPermissions()
        checkLocationEnabled()
        return try {
            suspendCancellableCoroutine { cont ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { coordinates ->
                        val location = coordinates?.let {
                            Coordinates(it.latitude, it.longitude)
                        }
                        cont.resume(location) { _, _, _ -> }
                    }.addOnFailureListener {
                        cont.resume(null) { _, _, _ -> }
                    }
            }
        } catch (e: SecurityException) {
            throw NoPermissionException("")
        }
    }

    suspend fun getLastLocation(): Coordinates? {
        Log.i("", "Getting last location")
        try {
            val coordinatesBytes = (context.dataStore.data.map {
                it[ConfigSettings.LAST_MAP_LOCATION]
            }.first()?.let {
                Base64.decode(it)
            }) ?: return null
            return Coordinates.fromByteArray(coordinatesBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
