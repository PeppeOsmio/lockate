package com.peppeosmio.lockate.ui.screens.circles

import com.peppeosmio.lockate.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CircleEditorCoordinatesTest {
    private val rome = Coordinates(41.9028, 12.4964)

    @Test
    fun `latitude and longitude are accepted with the usual separators`() {
        assertEquals(rome, parseCoordinates("41.9028, 12.4964"))
        assertEquals(rome, parseCoordinates("41.9028,12.4964"))
        assertEquals(rome, parseCoordinates("  41.9028   12.4964 "))
        assertEquals(rome, parseCoordinates("41.9028; 12.4964"))
    }

    @Test
    fun `negative values and the limits are accepted`() {
        assertEquals(Coordinates(-33.8688, -151.2093), parseCoordinates("-33.8688, -151.2093"))
        assertEquals(Coordinates(90.0, 180.0), parseCoordinates("90, 180"))
        assertEquals(Coordinates(-90.0, -180.0), parseCoordinates("-90, -180"))
    }

    @Test
    fun `values outside the range are refused`() {
        assertNull(parseCoordinates("90.1, 12"))
        assertNull(parseCoordinates("-91, 12"))
        assertNull(parseCoordinates("41, 180.1"))
        assertNull(parseCoordinates("41, -181"))
    }

    @Test
    fun `text that is not a pair of numbers is refused`() {
        assertNull(parseCoordinates(""))
        assertNull(parseCoordinates("41.9028"))
        assertNull(parseCoordinates("41.9028, 12.4964, 3"))
        assertNull(parseCoordinates("abc, def"))
        assertNull(parseCoordinates("41,9028, 12,4964"))
        assertNull(parseCoordinates("NaN, 12"))
    }

    @Test
    fun `the text written for a point is parsed back to it`() {
        assertEquals(rome, parseCoordinates(rome.toShareableString()))
    }

    @Test
    fun `a circle can't be saved while the coordinates text is not valid`() {
        val ready = CircleEditorState(
            isLoading = false, name = "Home", center = rome,
            coordinatesText = rome.toShareableString()
        )
        assertEquals(true, ready.canSave)
        assertFalse(ready.copy(coordinatesText = "41.9").canSave)
        assertFalse(ready.copy(coordinatesText = "").canSave)
    }
}
