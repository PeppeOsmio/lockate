package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingBeamTest {

    @Test
    fun `facing north the beam is centred on up`() {
        // canvas angles: 0 is east and they grow clockwise, so up is 270
        assertEquals(240f, beamStartAngle(headingDegrees = 0f, spreadDegrees = 60f), 0.001f)
    }

    @Test
    fun `facing east the beam is centred on the right`() {
        assertEquals(330f, beamStartAngle(headingDegrees = 90f, spreadDegrees = 60f), 0.001f)
    }

    @Test
    fun `facing south and west`() {
        assertEquals(60f, beamStartAngle(180f, 60f), 0.001f)
        assertEquals(150f, beamStartAngle(270f, 60f), 0.001f)
    }

    @Test
    fun `headings outside 0 to 360 are normalised`() {
        assertEquals(beamStartAngle(10f, 60f), beamStartAngle(370f, 60f), 0.001f)
        assertEquals(beamStartAngle(350f, 60f), beamStartAngle(-10f, 60f), 0.001f)
    }

    @Test
    fun `the result is always in 0 until 360`() {
        for (heading in -720..720 step 15) {
            val angle = beamStartAngle(heading.toFloat(), 70f)
            assert(angle >= 0f && angle < 360f) { "heading $heading gave $angle" }
        }
    }
}
