package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import kotlinx.datetime.LocalDateTime
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

    private var clock = 0

    // every call is a second later than the previous one
    private fun next() = at(++clock)

    private fun at(second: Int) =
        LocalDateTime(2026, 1, 1, 0, second / 60, second % 60)

    private fun tracker(vararg circles: Circle) = GeofenceTracker().also {
        it.replaceCircles(circles.toList())
    }

    private fun GeofenceTracker.update(position: Coordinates, member: String = "alice") =
        update(member, position, next())

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
    fun `members are tracked apart`() {
        val tracker = tracker(home)
        tracker.update(north(10.0), member = "alice")
        tracker.update(north(500.0), member = "bob")

        assertEquals(listOf(enter("home")), tracker.update(north(10.0), member = "bob"))
        assertTrue(tracker.update(north(10.0), member = "alice").isEmpty())
    }

    @Test
    fun `a seed is silent and the next position is compared to it`() {
        val tracker = tracker(home)

        tracker.seedMember("alice", north(10.0), next())

        assertEquals(listOf(exit("home")), tracker.update(north(300.0)))
    }

    @Test
    fun `a seed older than what is known is ignored`() {
        val tracker = tracker(home)
        tracker.seedMember("alice", north(500.0), at(10))
        tracker.update("alice", north(10.0), at(100))

        // a reconnect brings back the stored record from before the arrival
        tracker.seedMember("alice", north(500.0), at(50))

        assertTrue(tracker.update("alice", north(20.0), at(110)).isEmpty())
    }

    @Test
    fun `a newer seed replaces the state silently`() {
        val tracker = tracker(home)
        tracker.seedMember("alice", north(500.0), at(10))

        tracker.seedMember("alice", north(10.0), at(20))

        assertTrue(tracker.update("alice", north(20.0), at(30)).isEmpty())
        assertEquals(listOf(exit("home")), tracker.update("alice", north(300.0), at(40)))
    }

    @Test
    fun `a position older than what is known is ignored`() {
        val tracker = tracker(home)
        tracker.update("alice", north(10.0), at(100))

        assertTrue(tracker.update("alice", north(300.0), at(50)).isEmpty())
        assertTrue(tracker.update("alice", north(20.0), at(110)).isEmpty())
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
    fun `a circle moved onto a member takes the member as inside silently`() {
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
    fun `a removed member starts over silently`() {
        val tracker = tracker(home)
        tracker.update(north(10.0))

        tracker.removeMember("alice")

        assertTrue(tracker.update(north(300.0)).isEmpty())
    }

    @Test
    fun `members missing from the list are forgotten`() {
        val tracker = tracker(home)
        tracker.update(north(10.0), member = "alice")
        tracker.update(north(10.0), member = "bob")

        tracker.retainMembers(setOf("bob"))

        assertTrue(tracker.update(north(300.0), member = "alice").isEmpty())
        assertEquals(listOf(exit("home")), tracker.update(north(300.0), member = "bob"))
    }

    @Test
    fun `several circles report their own transitions`() {
        val work = home.copy(id = "work", center = north(1_000.0))
        val tracker = tracker(home, work)
        tracker.update(north(10.0))

        assertEquals(setOf(exit("home"), enter("work")), tracker.update(work.center).toSet())
    }
}
