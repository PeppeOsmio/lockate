package com.peppeosmio.lockate.service.anonymous_group

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PingClockTest {

    @Test
    fun `ticks on the wall-clock minute plus the jitter`() = runTest {
        val clock = PingClock(now = { 10_000 + currentTime }, jitterMillis = 500)

        val ticks = clock.ticks().take(3).map { 10_000 + currentTime }.toList()

        assertEquals(listOf(60_500L, 120_500L, 180_500L), ticks)
    }

    @Test
    fun `a socket opened later ticks with the others`() = runTest {
        val clock = PingClock(now = { 10_000 + currentTime }, jitterMillis = 500)

        val first = async { clock.ticks().take(2).map { 10_000 + currentTime }.toList() }
        val later = async {
            delay(15_000)
            clock.ticks().take(2).map { 10_000 + currentTime }.toList()
        }

        assertEquals(first.await(), later.await())
    }
}
