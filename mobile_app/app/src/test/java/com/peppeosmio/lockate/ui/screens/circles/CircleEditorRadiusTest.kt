package com.peppeosmio.lockate.ui.screens.circles

import com.peppeosmio.lockate.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleEditorRadiusTest {
    @Test
    fun `the ends of the slider are the limits of the range`() {
        assertEquals(30.0, sliderToRadius(0f), 0.0)
        assertEquals(5000.0, sliderToRadius(1f), 0.0)
        assertEquals(0f, radiusToSlider(30.0), 0f)
        assertEquals(1f, radiusToSlider(5000.0), 0f)
    }

    @Test
    fun `the scale is logarithmic, so the middle is about 390 m`() {
        assertEquals(390.0, sliderToRadius(0.5f), 0.0)
    }

    @Test
    fun `radii are rounded to 10 m below 1 km and to 50 m above`() {
        assertEquals(0, (sliderToRadius(0.3f) % 10).toInt())
        assertEquals(0, (sliderToRadius(0.8f) % 50).toInt())
    }

    @Test
    fun `a preset goes back to itself through the slider`() {
        for (preset in CircleEditorViewModel.RADIUS_PRESETS) {
            assertEquals(preset, sliderToRadius(radiusToSlider(preset)), 0.0)
        }
    }

    @Test
    fun `radii read in meters, then in kilometers`() {
        assertEquals("250 m", formatRadius(250.0))
        assertEquals("1 km", formatRadius(1000.0))
        assertEquals("1.5 km", formatRadius(1500.0))
    }

    @Test
    fun `a typed radius is accepted only inside the range`() {
        assertEquals(30.0, parseRadiusMeters("30")!!, 0.0)
        assertEquals(5000.0, parseRadiusMeters("5000")!!, 0.0)
        assertEquals(150.0, parseRadiusMeters("0150")!!, 0.0)
        assertNull(parseRadiusMeters("29"))
        assertNull(parseRadiusMeters("5001"))
        assertNull(parseRadiusMeters(""))
        assertNull(parseRadiusMeters("12.5"))
    }

    private fun readyState(
        name: String = "Home",
        isDeleting: Boolean = false,
        radiusText: String = "250",
    ) =
        CircleEditorState(
            isLoading = false,
            name = name,
            center = Coordinates(40.0, 14.0),
            isDeleting = isDeleting,
            radiusText = radiusText,
        )

    @Test
    fun `a named circle with a point can be saved`() {
        assertTrue(readyState().canSave)
    }

    @Test
    fun `a circle can't be saved without a name or while it is being deleted`() {
        assertFalse(readyState(name = " ").canSave)
        assertFalse(readyState(isDeleting = true).canSave)
    }

    @Test
    fun `a circle can't be saved while the typed radius is not valid`() {
        assertFalse(readyState(radiusText = "").canSave)
        assertFalse(readyState(radiusText = "10").canSave)
    }
}
