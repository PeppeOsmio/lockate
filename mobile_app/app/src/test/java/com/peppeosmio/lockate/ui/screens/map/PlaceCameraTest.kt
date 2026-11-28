package com.peppeosmio.lockate.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCameraTest {

    @Test
    fun `a 100 m place on a 400 dp wide map at the equator`() {
        assertEquals(16.52, zoomToFitPlace(0.0, 100.0, 400.0), 0.01)
    }

    @Test
    fun `doubling the radius zooms out by one level`() {
        val small = zoomToFitPlace(40.0, 100.0, 400.0)
        val big = zoomToFitPlace(40.0, 200.0, 400.0)

        assertEquals(1.0, small - big, 1e-9)
    }

    @Test
    fun `a wider map zooms in by the same amount`() {
        val narrow = zoomToFitPlace(40.0, 100.0, 400.0)
        val wide = zoomToFitPlace(40.0, 100.0, 800.0)

        assertEquals(1.0, wide - narrow, 1e-9)
    }

    @Test
    fun `at 60 degrees of latitude the same place needs one level less`() {
        val equator = zoomToFitPlace(0.0, 100.0, 400.0)
        val north = zoomToFitPlace(60.0, 100.0, 400.0)

        assertEquals(1.0, equator - north, 1e-9)
    }

    @Test
    fun `a place taking twice the share of the width zooms in by one level`() {
        val third = zoomToFitPlace(40.0, 100.0, 400.0, widthFraction = 0.3)
        val most = zoomToFitPlace(40.0, 100.0, 400.0, widthFraction = 0.6)

        assertEquals(1.0, most - third, 1e-9)
    }

    @Test
    fun `the zoom stays between 1 and 20`() {
        assertEquals(20.0, zoomToFitPlace(40.0, 0.01, 400.0), 0.0)
        assertEquals(1.0, zoomToFitPlace(40.0, 5_000_000.0, 400.0), 0.0)
    }

    @Test
    fun `the width fraction agrees with the zoom that fits the place`() {
        val zoom = zoomToFitPlace(40.0, 250.0, 411.0, widthFraction = 0.6)

        assertEquals(0.6, placeWidthFraction(40.0, 250.0, zoom, 411.0), 1e-9)
    }

    @Test
    fun `zooming in by one level doubles the width fraction`() {
        val before = placeWidthFraction(40.0, 250.0, 14.0, 411.0)
        val after = placeWidthFraction(40.0, 250.0, 15.0, 411.0)

        assertEquals(2.0, after / before, 1e-9)
    }
}
