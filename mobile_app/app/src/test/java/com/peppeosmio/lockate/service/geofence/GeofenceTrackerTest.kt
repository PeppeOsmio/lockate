package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationFix
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceTrackerTest {

    private val home =
        Circle(
            id = "home",
            name = "Home",
            center = Coordinates(40.0, 14.0),
            radiusMeters = 100.0,
        )

    // meters north of the center of home
    private fun north(meters: Double) =
        Coordinates(home.center.latitude + meters / 111_195.0, home.center.longitude)

    private fun tracker(vararg circles: Circle) =
        GeofenceTracker().also {
            it.replaceCircles(circles.toList())
        }

    // a fix whose transitions the server gets at once
    private fun GeofenceTracker.update(
        position: Coordinates,
        accuracy: Float = 10f,
        nowMillis: Long = 0,
    ) = update(LocationFix(position, accuracy, 0), nowMillis).also { confirm(it, nowMillis) }

    private fun GeofenceTracker.unsent(position: Coordinates, nowMillis: Long = 0) =
        update(LocationFix(position, 10f, 0), nowMillis)

    private fun enter(circle: String) = CircleTransition(circle, TransitionKind.Enter)

    private fun exit(circle: String) = CircleTransition(circle, TransitionKind.Exit)

    @Test
    fun `the first position sets the state without a transition`() {
        val tracker = tracker(home)

        assertTrue(tracker.update(north(10.0)).isEmpty())
        assertEquals(listOf(exit("home")), tracker.update(north(300.0)))
    }

    @Test
    fun `the first position outside sets the state to outside without a transition`() {
        val tracker = tracker(home)

        assertTrue(tracker.update(north(500.0)).isEmpty())
        assertEquals(listOf(enter("home")), tracker.update(north(50.0)))
    }

    @Test
    fun `entering announces an arrival once`() {
        val tracker = tracker(home)
        tracker.update(north(500.0))

        assertEquals(listOf(enter("home")), tracker.update(north(50.0)))
        assertTrue(tracker.update(north(40.0)).isEmpty())
    }

    @Test
    fun `leaving announces a departure once`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        assertEquals(listOf(exit("home")), tracker.update(north(300.0)))
        assertTrue(tracker.update(north(400.0)).isEmpty())
    }

    @Test
    fun `wobbling on the edge doesn't produce transitions`() {
        val tracker = tracker(home)
        tracker.update(north(50.0))

        val fixes = listOf(95.0, 105.0, 98.0, 110.0, 99.0, 101.0)

        assertTrue(fixes.flatMap { tracker.update(north(it)) }.isEmpty())
    }

    @Test
    fun `the hysteresis margin never exceeds a fifth of the radius`() {
        val small = home.copy(id = "small", radiusMeters = 30.0)
        val tracker = tracker(small)
        tracker.update(north(10.0))

        // 30 m radius: margin is 6 m, so 37 m is outside
        assertEquals(listOf(exit("small")), tracker.update(north(37.0)))
    }

    @Test
    fun `a circle created while inside doesn't announce an arrival`() {
        val tracker = tracker()
        tracker.update(north(10.0))

        tracker.upsertCircle(home)

        assertTrue(tracker.update(north(10.0)).isEmpty())
        assertEquals(listOf(exit("home")), tracker.update(north(300.0)))
    }

    @Test
    fun `a circle learned after the fix that crossed it announces the crossing on that fix`() {
        val tracker = tracker()
        tracker.update(north(500.0))
        tracker.unsent(north(50.0))

        tracker.upsertCircle(home)

        assertEquals(listOf(enter("home")), tracker.unsent(north(50.0)))
    }

    @Test
    fun `a circle crossed while the posts failed is announced when we learn of it`() {
        val tracker = tracker()
        tracker.update(north(500.0), nowMillis = 0)
        tracker.unsent(north(300.0), nowMillis = 1_000)
        tracker.unsent(north(50.0), nowMillis = 2_000)
        tracker.unsent(north(40.0), nowMillis = 3_000)

        tracker.upsertCircle(home)

        assertEquals(listOf(enter("home")), tracker.unsent(north(40.0), nowMillis = 4_000))
    }

    @Test
    fun `a circle created around us while we stand still announces nothing`() {
        val tracker = tracker()
        tracker.update(north(10.0))
        tracker.unsent(north(10.0))

        tracker.upsertCircle(home)

        assertTrue(tracker.unsent(north(10.0)).isEmpty())
    }

    @Test
    fun `a crossing older than the pending limit isn't announced`() {
        val tracker = tracker()
        tracker.update(north(500.0), nowMillis = 0)
        tracker.unsent(north(50.0), nowMillis = GeofenceTracker.MAX_PENDING_MILLIS)

        tracker.upsertCircle(home)

        assertTrue(
            tracker.unsent(north(50.0), nowMillis = GeofenceTracker.MAX_PENDING_MILLIS).isEmpty()
        )
    }

    @Test
    fun `a circle moved onto us takes us as inside silently`() {
        val tracker = tracker(home)
        tracker.update(north(1_000.0))

        tracker.upsertCircle(home.copy(center = north(1_000.0)))

        assertTrue(tracker.update(north(1_010.0)).isEmpty())
        assertEquals(listOf(exit("home")), tracker.update(north(1_500.0)))
    }

    @Test
    fun `a circle renamed keeps its state`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))
        // inside the margin: still inside, though a fresh look would say outside
        tracker.update(north(110.0))

        tracker.upsertCircle(home.copy(name = "Casa"))

        assertTrue(tracker.update(north(110.0)).isEmpty())
        assertEquals(listOf(exit("home")), tracker.update(north(300.0)))
    }

    @Test
    fun `a replaced circle list drops the missing circles`() {
        val work = home.copy(id = "work", center = north(1_000.0))
        val tracker = tracker(home, work)
        tracker.update(north(10.0))

        tracker.replaceCircles(listOf(work))

        assertEquals(listOf(enter("work")), tracker.update(work.center))
    }

    @Test
    fun `removed circles are forgotten`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        tracker.removeCircle("home")

        assertTrue(tracker.update(north(300.0)).isEmpty())
    }

    @Test
    fun `several circles report their own transitions`() {
        val work = home.copy(id = "work", center = north(1_000.0))
        val tracker = tracker(home, work)
        tracker.update(north(10.0))

        assertEquals(setOf(exit("home"), enter("work")), tracker.update(work.center).toSet())
    }

    @Test
    fun `inaccurate fixes are ignored`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        assertTrue(tracker.update(north(300.0), accuracy = 80f).isEmpty())
        assertEquals(listOf(exit("home")), tracker.update(north(300.0)))
    }

    @Test
    fun `an inaccurate first fix doesn't seed the state`() {
        val tracker = tracker(home)
        tracker.update(north(10.0), accuracy = 80f)

        assertTrue(tracker.update(north(300.0)).isEmpty())
        assertEquals(listOf(enter("home")), tracker.update(north(10.0)))
    }

    @Test
    fun `after forgetting the position the next fix seeds silently`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        tracker.forgetPosition()

        assertTrue(tracker.update(north(300.0)).isEmpty())
        assertEquals(listOf(enter("home")), tracker.update(north(10.0)))
    }

    @Test
    fun `an unconfirmed transition comes back with the next fix`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        assertEquals(listOf(exit("home")), tracker.unsent(north(300.0)))
        assertEquals(listOf(exit("home")), tracker.unsent(north(310.0), nowMillis = 2_000))
    }

    @Test
    fun `an inaccurate fix still returns the unconfirmed transitions`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))
        tracker.unsent(north(300.0))

        assertEquals(
            listOf(exit("home")),
            tracker.update(LocationFix(north(10.0), 80f, 0), nowMillis = 1_000),
        )
    }

    @Test
    fun `an exit and an entry both unconfirmed cancel out`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))
        tracker.unsent(north(300.0))

        assertTrue(tracker.unsent(north(10.0), nowMillis = 1_000).isEmpty())
    }

    @Test
    fun `a transition unconfirmed for too long is dropped`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))
        tracker.unsent(north(300.0), nowMillis = 0)

        assertTrue(
            tracker.unsent(north(310.0), nowMillis = GeofenceTracker.MAX_PENDING_MILLIS).isEmpty()
        )
        assertEquals(
            listOf(enter("home")),
            tracker.unsent(north(10.0), nowMillis = GeofenceTracker.MAX_PENDING_MILLIS + 1),
        )
    }

    @Test
    fun `a confirmation that a later fix overtook leaves the new transition pending`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))
        val sent = tracker.unsent(north(300.0))
        tracker.unsent(north(10.0), nowMillis = 1_000)

        tracker.confirm(sent, nowMillis = 2_000)

        assertEquals(listOf(enter("home")), tracker.unsent(north(10.0), nowMillis = 3_000))
    }

    @Test
    fun `confirming a removed circle does nothing`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))
        val sent = tracker.unsent(north(300.0))
        tracker.removeCircle("home")

        tracker.confirm(sent, nowMillis = 1_000)

        assertTrue(tracker.unsent(north(10.0), nowMillis = 2_000).isEmpty())
    }
}
