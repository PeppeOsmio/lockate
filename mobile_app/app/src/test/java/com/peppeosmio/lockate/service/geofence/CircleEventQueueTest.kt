package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleEventQueueTest {
    private val enterHome = CircleTransition("home", TransitionKind.Enter)
    private val exitHome = CircleTransition("home", TransitionKind.Exit)
    private val enterSchool = CircleTransition("school", TransitionKind.Enter)

    private fun CircleEventQueue.drainTransitions(nowMillis: Long) =
        drain(nowMillis).map { it.transition }

    @Test
    fun `events are sent oldest first and only once`() {
        val queue = CircleEventQueue()
        queue.add(enterHome, 0)
        queue.add(exitHome, 1_000)

        assertEquals(listOf(enterHome, exitHome), queue.drainTransitions(2_000))
        assertTrue(queue.drain(3_000).isEmpty())
    }

    @Test
    fun `events older than the limit are dropped`() {
        val queue = CircleEventQueue()
        queue.add(enterHome, 0)
        queue.add(exitHome, 60_000)

        assertEquals(listOf(exitHome), queue.drainTransitions(CircleEventQueue.MAX_AGE_MILLIS))
        assertTrue(queue.drain(CircleEventQueue.MAX_AGE_MILLIS).isEmpty())
    }

    @Test
    fun `events put back go out again before newer ones and keep their age`() {
        val queue = CircleEventQueue()
        queue.add(enterHome, 0)
        queue.add(exitHome, 60_000)
        val unsent = queue.drain(1_000)
        queue.add(enterSchool, 120_000)
        queue.putBack(unsent)

        assertEquals(
            listOf(exitHome, enterSchool),
            queue.drainTransitions(CircleEventQueue.MAX_AGE_MILLIS),
        )
    }
}
