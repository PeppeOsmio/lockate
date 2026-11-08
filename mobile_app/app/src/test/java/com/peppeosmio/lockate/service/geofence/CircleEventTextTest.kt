package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CircleEventTextTest {
    private val home = Circle("home", "Home", Coordinates(40.0, 14.0), 100.0)
    private val school = Circle("school", "School", Coordinates(41.0, 14.0), 100.0)
    private val circles = listOf(home, school).associateBy { it.id }

    @Test
    fun `an arrival and a departure are described`() {
        val texts =
            circleEventTexts(
                memberId = "m1",
                memberName = "Mario",
                transitions =
                    listOf(
                        CircleTransition("home", TransitionKind.Enter),
                        CircleTransition("school", TransitionKind.Exit),
                    ),
                circles = circles,
                mutedTargets = emptySet(),
            )

        assertEquals(listOf("Mario arrived at Home", "Mario left School"), texts)
    }

    @Test
    fun `a muted circle is skipped`() {
        val texts =
            circleEventTexts(
                "m1",
                "Mario",
                listOf(
                    CircleTransition("home", TransitionKind.Enter),
                    CircleTransition("school", TransitionKind.Enter),
                ),
                circles,
                mutedTargets = setOf("home"),
            )

        assertEquals(listOf("Mario arrived at School"), texts)
    }

    @Test
    fun `a muted member is skipped`() {
        val texts =
            circleEventTexts(
                "m1",
                "Mario",
                listOf(CircleTransition("home", TransitionKind.Enter)),
                circles,
                mutedTargets = setOf("m1"),
            )

        assertEquals(emptyList<String>(), texts)
    }

    @Test
    fun `a circle we don't know is skipped`() {
        val texts =
            circleEventTexts(
                "m1",
                "Mario",
                listOf(CircleTransition("gone", TransitionKind.Enter)),
                circles,
                emptySet(),
            )

        assertEquals(emptyList<String>(), texts)
    }
}
