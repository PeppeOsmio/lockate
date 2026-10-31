package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerMotionTest {

    private val from = Coordinates(latitude = 40.0, longitude = 14.0)
    private val to = Coordinates(latitude = 40.002, longitude = 14.004)

    @Test
    fun `fraction 0 gives the start and 1 gives the end`() {
        assertEquals(from, lerpCoordinates(from, to, 0f))
        assertEquals(to, lerpCoordinates(from, to, 1f))
    }

    @Test
    fun `fraction 0_5 gives the midpoint`() {
        val mid = lerpCoordinates(from, to, 0.5f)
        assertEquals(40.001, mid.latitude, 1e-9)
        assertEquals(14.002, mid.longitude, 1e-9)
    }

    @Test
    fun `crossing the antimeridian takes the short way`() {
        val a = Coordinates(latitude = 0.0, longitude = 179.9)
        val b = Coordinates(latitude = 0.0, longitude = -179.9)
        val mid = lerpCoordinates(a, b, 0.5f)
        assertEquals(180.0, kotlin.math.abs(mid.longitude), 1e-9)
        assertEquals(b.longitude, lerpCoordinates(a, b, 1f).longitude, 1e-9)
    }

    @Test
    fun `longitude stays within -180 to 180`() {
        val a = Coordinates(latitude = 0.0, longitude = 179.9)
        val b = Coordinates(latitude = 0.0, longitude = -179.9)
        for (i in 0..10) {
            val lng = lerpCoordinates(a, b, i / 10f).longitude
            assertTrue("fraction ${i / 10f} gave $lng", lng >= -180.0 && lng <= 180.0)
        }
    }

    @Test
    fun `short moves are animated`() {
        assertFalse(shouldSnap(from, to))
    }

    @Test
    fun `identical positions do not snap`() {
        assertFalse(shouldSnap(from, from))
    }

    @Test
    fun `moves beyond the threshold snap`() {
        // 0.02 degrees of latitude is about 2.2 km
        assertTrue(shouldSnap(from, Coordinates(latitude = 40.02, longitude = 14.0)))
    }
}
