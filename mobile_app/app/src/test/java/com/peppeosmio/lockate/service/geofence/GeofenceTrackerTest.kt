package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceTrackerTest {

    private val home = Circle(
        id = "home", name = "Home", center = Coordinates(40.0, 14.0), radiusMeters = 100.0
    )

    // meters north of the center of home
    private fun north(meters: Double) =
        Coordinates(home.center.latitude + meters / 111_195.0, home.center.longitude)

    private fun tracker(vararg circles: Circle) = GeofenceTracker().also {
        it.replaceCircles(circles.toList())
    }

    @Test
    fun `the first fix sets the state without a transition`() {
        val tracker = tracker(home)

        val result = tracker.update(north(10.0))

        assertTrue(result.transitions.isEmpty())
        assertEquals(mapOf("home" to true), result.changedStates)
        assertEquals(mapOf("home" to true), tracker.states())
    }

    @Test
    fun `the first fix outside sets the state to outside without a transition`() {
        val tracker = tracker(home)

        val result = tracker.update(north(500.0))

        assertTrue(result.transitions.isEmpty())
        assertEquals(mapOf("home" to false), tracker.states())
    }

    @Test
    fun `entering announces an arrival once`() {
        val tracker = tracker(home)
        tracker.update(north(500.0))

        val first = tracker.update(north(50.0))
        val second = tracker.update(north(40.0))

        assertEquals(listOf(CircleTransition("home", TransitionKind.Enter)), first.transitions)
        assertTrue(second.transitions.isEmpty())
    }

    @Test
    fun `leaving announces a departure once`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        val first = tracker.update(north(300.0))
        val second = tracker.update(north(400.0))

        assertEquals(listOf(CircleTransition("home", TransitionKind.Exit)), first.transitions)
        assertTrue(second.transitions.isEmpty())
    }

    @Test
    fun `wobbling on the edge doesn't produce transitions`() {
        val tracker = tracker(home)
        tracker.update(north(50.0))

        val fixes = listOf(95.0, 105.0, 98.0, 110.0, 99.0, 101.0)
        val transitions = fixes.flatMap { tracker.update(north(it)).transitions }

        assertTrue(transitions.isEmpty())
    }

    @Test
    fun `the hysteresis margin never exceeds a fifth of the radius`() {
        val small = home.copy(id = "small", radiusMeters = 30.0)
        val tracker = tracker(small)
        tracker.update(north(10.0))

        // 30 m radius: margin is 6 m, so 37 m is outside
        val result = tracker.update(north(37.0))

        assertEquals(listOf(CircleTransition("small", TransitionKind.Exit)), result.transitions)
    }

    @Test
    fun `a circle created while inside doesn't announce an arrival`() {
        val tracker = tracker()
        tracker.update(north(10.0))

        tracker.upsertCircle(home)
        val result = tracker.update(north(10.0))

        assertTrue(result.transitions.isEmpty())
        assertEquals(mapOf("home" to true), tracker.states())
    }

    @Test
    fun `a circle moved while inside doesn't announce anything`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        tracker.upsertCircle(home.copy(center = Coordinates(40.0, 14.0001)))
        val result = tracker.update(north(10.0))

        assertTrue(result.transitions.isEmpty())
    }

    @Test
    fun `a circle renamed keeps its state`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        tracker.upsertCircle(home.copy(name = "Casa"))
        val result = tracker.update(north(300.0))

        assertEquals(listOf(CircleTransition("home", TransitionKind.Exit)), result.transitions)
    }

    @Test
    fun `a replaced circle list keeps the state of unchanged circles only`() {
        val work = home.copy(id = "work", center = Coordinates(41.0, 14.0))
        val tracker = tracker(home, work)
        tracker.update(north(10.0))

        tracker.replaceCircles(listOf(home, work.copy(radiusMeters = 500.0)))

        assertEquals(mapOf("home" to true), tracker.states())
    }

    @Test
    fun `removed circles are forgotten`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        tracker.removeCircle("home")

        assertTrue(tracker.states().isEmpty())
        assertTrue(tracker.update(north(300.0)).transitions.isEmpty())
    }

    @Test
    fun `restored states are used and a restart inside doesn't announce an arrival`() {
        val tracker = tracker(home)
        tracker.restoreStates(mapOf("home" to true))

        val result = tracker.update(north(10.0))

        assertTrue(result.transitions.isEmpty())
        assertTrue(result.changedStates.isEmpty())
    }

    @Test
    fun `several circles report their own transitions`() {
        val work = home.copy(id = "work", center = north(1_000.0))
        val tracker = tracker(home, work)
        tracker.update(north(10.0))

        val result = tracker.update(work.center)

        assertEquals(
            setOf(
                CircleTransition("home", TransitionKind.Exit),
                CircleTransition("work", TransitionKind.Enter)
            ), result.transitions.toSet()
        )
    }
}
