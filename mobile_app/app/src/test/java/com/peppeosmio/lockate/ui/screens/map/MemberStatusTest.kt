package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.Place
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTime::class)
class MemberStatusTest {

    private val now = Instant.parse("2026-11-22T13:42:00Z")
    private val home = Coordinates(latitude = 40.85, longitude = 14.25)
    private val places =
        listOf(Place(id = "home", name = "Home", center = home, radiusMeters = 250.0))

    private fun member(
        id: String = "marco",
        coordinates: Coordinates? = home,
        activity: UserActivity = UserActivity.Still,
        age: Duration = Duration.ZERO,
    ) =
        AGMember(
            internalId = 1,
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            createdAt = now.toLocalDateTime(TimeZone.UTC),
            lastLocationRecord =
                coordinates?.let {
                    LocationRecord(it, (now - age).toLocalDateTime(TimeZone.UTC), activity)
                },
            isAGAdmin = false,
        )

    private fun status(member: AGMember) = memberStatus(member, places, now)

    @Test
    fun `no position yet`() {
        assertEquals("No location yet", status(member(coordinates = null)))
    }

    @Test
    fun `still inside a place`() {
        assertEquals("At Home · still · just now", status(member()))
    }

    @Test
    fun `moving outside places`() {
        val away = Coordinates(latitude = 40.86, longitude = 14.25)
        assertEquals(
            "On foot · 1 min ago",
            status(member(coordinates = away, activity = UserActivity.OnFoot, age = 1.minutes)),
        )
    }

    @Test
    fun `an old position means sharing is paused`() {
        assertEquals("Paused sharing · 42 min ago", status(member(age = 42.minutes)))
    }

    @Test
    fun `who is in a place`() {
        val me = member(id = "giulia")
        val marco = member(id = "marco")
        val sara = member(id = "sara")
        val luca = member(id = "luca")
        assertEquals("You're here", whoIsHere(listOf(me), "giulia"))
        assertEquals("Marco is here", whoIsHere(listOf(marco), "giulia"))
        assertEquals("You and Marco are here", whoIsHere(listOf(marco, me), "giulia"))
        assertEquals(
            "You, Luca and 2 more are here",
            whoIsHere(listOf(sara, marco, luca, me), "giulia"),
        )
    }
}
