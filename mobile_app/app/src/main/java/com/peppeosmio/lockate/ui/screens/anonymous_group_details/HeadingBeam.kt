package com.peppeosmio.lockate.ui.screens.anonymous_group_details

/**
 * Start angle, in canvas degrees (0 = east, clockwise, in 0 until 360), of a beam of
 * [spreadDegrees] centred on a compass heading (0 = north, clockwise).
 */
fun beamStartAngle(headingDegrees: Float, spreadDegrees: Float): Float {
    val start = (headingDegrees - 90f - spreadDegrees / 2f) % 360f
    return if (start < 0f) start + 360f else start
}
