package com.peppeosmio.lockate.ui.screens.circles

import com.peppeosmio.lockate.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleEditorRadiusTest {
    @Test
    fun `a radius inside the range is parsed`() {
        assertEquals(150.0, parseRadiusMeters("150")!!, 0.0)
        assertEquals(500.0, parseRadiusMeters("0500")!!, 0.0)
    }

    @Test
    fun `the limits of the range are accepted`() {
        assertEquals(30.0, parseRadiusMeters("30")!!, 0.0)
        assertEquals(2000.0, parseRadiusMeters("2000")!!, 0.0)
    }

    @Test
    fun `values outside the range are refused`() {
        assertNull(parseRadiusMeters("29"))
        assertNull(parseRadiusMeters("2001"))
        assertNull(parseRadiusMeters("0"))
    }

    @Test
    fun `an empty or non numeric text is refused`() {
        assertNull(parseRadiusMeters(""))
        assertNull(parseRadiusMeters("abc"))
        assertNull(parseRadiusMeters("12.5"))
        assertNull(parseRadiusMeters("-100"))
    }

    private fun readyState(radiusText: String) =
        CircleEditorState(
            isLoading = false,
            name = "Home",
            center = Coordinates(40.0, 14.0),
            radiusText = radiusText,
        )

    @Test
    fun `a circle with a valid radius can be saved`() {
        assertTrue(readyState("150").canSave)
    }

    @Test
    fun `a circle can't be saved while the radius text is not valid`() {
        assertFalse(readyState("").canSave)
        assertFalse(readyState("5").canSave)
        assertFalse(readyState("3000").canSave)
    }
}
