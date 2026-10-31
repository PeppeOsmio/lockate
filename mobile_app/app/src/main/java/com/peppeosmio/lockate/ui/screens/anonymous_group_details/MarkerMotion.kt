package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.peppeosmio.lockate.domain.Coordinates
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Shorter than the ~2 s location update cadence, so a marker settles before the next fix
const val MARKER_MOVE_DURATION_MS = 1000

// A longer jump (a stale record replaced, the first fix after hours) would sweep across the map
const val MAX_ANIMATED_DISTANCE_METERS = 1000.0

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun lerpCoordinates(from: Coordinates, to: Coordinates, fraction: Float): Coordinates {
    var longitudeDelta = to.longitude - from.longitude
    if (longitudeDelta > 180.0) longitudeDelta -= 360.0
    if (longitudeDelta < -180.0) longitudeDelta += 360.0
    var longitude = from.longitude + longitudeDelta * fraction
    if (longitude > 180.0) longitude -= 360.0
    if (longitude < -180.0) longitude += 360.0
    return Coordinates(
        latitude = from.latitude + (to.latitude - from.latitude) * fraction, longitude = longitude
    )
}

fun shouldSnap(from: Coordinates, to: Coordinates): Boolean {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
    val distance = 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    return distance > MAX_ANIMATED_DISTANCE_METERS
}

/**
 * Follows [target] by gliding from wherever the value currently is, so a fix arriving mid-glide
 * continues smoothly instead of jumping back. The first value is not animated.
 */
@Composable
fun animateCoordinatesAsState(target: Coordinates): State<Coordinates> {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        val current = lerpCoordinates(from, to, progress.value)
        from = current
        to = target
        if (shouldSnap(current, target)) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(MARKER_MOVE_DURATION_MS, easing = FastOutSlowInEasing))
        }
    }

    return remember { derivedStateOf { lerpCoordinates(from, to, progress.value) } }
}
