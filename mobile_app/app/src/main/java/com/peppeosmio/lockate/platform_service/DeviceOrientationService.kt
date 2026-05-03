package com.peppeosmio.lockate.platform_service

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.FusedOrientationProviderClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.concurrent.Executor
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

class DeviceOrientationService(
    private val context: Context,
    private val fusedOrientationProviderClient: FusedOrientationProviderClient,
) {

    private val _orientationUpdates =
        MutableSharedFlow<Float>(extraBufferCapacity = 1)

    @OptIn(ExperimentalAtomicApi::class)
    private val activeCollectors = AtomicInt(0)
    private var orientationListener: DeviceOrientationListener? = null

    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(context) }

    @Throws
    fun getOrientationUpdates(): Flow<Float> = flow {
        onCollectorAdded()
        try {
            emitAll(_orientationUpdates)
        } finally {
            onCollectorRemoved()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun onCollectorAdded() {
        if (activeCollectors.incrementAndFetch() == 1) {
            startOrientationUpdates()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun onCollectorRemoved() {
        if (activeCollectors.decrementAndFetch() == 0) {
            stopOrientationUpdates()
        }
    }

    private fun startOrientationUpdates() {
        val request = DeviceOrientationRequest.Builder(20_000L).build()

        orientationListener = DeviceOrientationListener { deviceOrientation ->
            _orientationUpdates.tryEmit(deviceOrientation.headingDegrees)
        }

        fusedOrientationProviderClient.requestOrientationUpdates(
            request, mainExecutor, orientationListener!!
        )
    }

    private fun stopOrientationUpdates() {
        orientationListener?.let {
            fusedOrientationProviderClient.removeOrientationUpdates(it)
        }
        orientationListener = null
    }
}
