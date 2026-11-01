package com.peppeosmio.lockate.ui.screens.map

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow

private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
// the world is 512 dp wide at zoom 0
private const val WORLD_WIDTH_DP_AT_ZOOM_0 = 512.0
private const val MIN_ZOOM = 1.0
private const val MAX_ZOOM = 20.0

/** The zoom at which a circle's diameter takes [widthFraction] of a map [viewportWidthDp] wide. */
fun zoomToFitCircle(
    latitude: Double,
    radiusMeters: Double,
    viewportWidthDp: Double,
    widthFraction: Double = 0.6
): Double {
    val metersPerDp = 2 * radiusMeters / (widthFraction * viewportWidthDp)
    val zoom = ln(
        EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(latitude)) /
                (WORLD_WIDTH_DP_AT_ZOOM_0 * metersPerDp)
    ) / ln(2.0)
    return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
}

/** The share of a map [viewportWidthDp] wide that a circle's diameter takes at [zoom]. */
fun circleWidthFraction(
    latitude: Double, radiusMeters: Double, zoom: Double, viewportWidthDp: Double
): Double {
    val metersPerDp = EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(latitude)) /
            (WORLD_WIDTH_DP_AT_ZOOM_0 * 2.0.pow(zoom))
    return 2 * radiusMeters / metersPerDp / viewportWidthDp
}
