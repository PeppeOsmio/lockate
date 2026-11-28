package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceTest {
    private val origin = Coordinates(40.0, 14.0)

    // meters north of the origin
    private fun north(meters: Double) =
        Coordinates(origin.latitude + meters / 111_195.0, origin.longitude)

    private fun place(id: String, center: Coordinates, radiusMeters: Double) =
        Place(id, id, center, radiusMeters)

    @Test
    fun `a point inside a place finds it`() {
        val home = place("home", origin, 100.0)

        assertEquals(home, listOf(home).nearestContaining(north(50.0)))
    }

    @Test
    fun `a point outside every place finds none`() {
        val home = place("home", origin, 100.0)

        assertNull(listOf(home).nearestContaining(north(150.0)))
    }

    @Test
    fun `no places find none`() {
        assertNull(emptyList<Place>().nearestContaining(origin))
    }

    @Test
    fun `the centre is inside`() {
        val home = place("home", origin, 1.0)

        assertEquals(home, listOf(home).nearestContaining(origin))
    }

    @Test
    fun `overlapping places pick the one whose centre is closest`() {
        val big = place("big", origin, 2_000.0)
        val small = place("small", north(800.0), 300.0)

        // 600 m from the centre of big, 200 m from the centre of small
        assertEquals(small, listOf(big, small).nearestContaining(north(600.0)))
        assertEquals(small, listOf(small, big).nearestContaining(north(600.0)))
    }

    @Test
    fun `the closest centre wins even when its place is the bigger one`() {
        val big = place("big", origin, 2_000.0)
        val small = place("small", north(800.0), 600.0)

        // inside both, 350 m from the centre of big and 450 m from the centre of small
        assertEquals(big, listOf(small, big).nearestContaining(north(350.0)))
    }

    @Test
    fun `a place that doesn't contain the point isn't picked for being closer`() {
        val tight = place("tight", north(300.0), 50.0)
        val wide = place("wide", origin, 1_000.0)

        // 100 m from the centre of tight but outside it, and inside wide
        assertEquals(wide, listOf(tight, wide).nearestContaining(north(200.0)))
    }
}
