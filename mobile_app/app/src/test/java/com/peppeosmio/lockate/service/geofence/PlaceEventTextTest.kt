package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.domain.anonymous_group.PlaceTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceEventTextTest {
    private val home = Place("home", "Home", Coordinates(40.0, 14.0), 100.0)
    private val school = Place("school", "School", Coordinates(41.0, 14.0), 100.0)
    private val places = listOf(home, school).associateBy { it.id }

    @Test
    fun `an arrival and a departure are described`() {
        val texts =
            placeEventTexts(
                memberId = "m1",
                memberName = "Mario",
                transitions =
                    listOf(
                        PlaceTransition("home", TransitionKind.Enter),
                        PlaceTransition("school", TransitionKind.Exit),
                    ),
                places = places,
                mutedTargets = emptySet(),
            )

        assertEquals(listOf("Mario arrived at Home", "Mario left School"), texts)
    }

    @Test
    fun `a muted place is skipped`() {
        val texts =
            placeEventTexts(
                "m1",
                "Mario",
                listOf(
                    PlaceTransition("home", TransitionKind.Enter),
                    PlaceTransition("school", TransitionKind.Enter),
                ),
                places,
                mutedTargets = setOf("home"),
            )

        assertEquals(listOf("Mario arrived at School"), texts)
    }

    @Test
    fun `a muted member is skipped`() {
        val texts =
            placeEventTexts(
                "m1",
                "Mario",
                listOf(PlaceTransition("home", TransitionKind.Enter)),
                places,
                mutedTargets = setOf("m1"),
            )

        assertEquals(emptyList<String>(), texts)
    }

    @Test
    fun `a place we don't know is skipped`() {
        val texts =
            placeEventTexts(
                "m1",
                "Mario",
                listOf(PlaceTransition("gone", TransitionKind.Enter)),
                places,
                emptySet(),
            )

        assertEquals(emptyList<String>(), texts)
    }
}
