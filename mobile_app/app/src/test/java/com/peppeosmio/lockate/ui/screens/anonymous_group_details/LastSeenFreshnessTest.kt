package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTime::class)
class LastSeenFreshnessTest {

    private val lastSeen = LocalDateTime(2026, 10, 3, 15, 30, 0)

    private fun freshnessAfter(elapsed: Duration) =
        lastSeenFreshness(lastSeen, lastSeen.toInstant(TimeZone.UTC) + elapsed)

    @Test
    fun `within the last 15 minutes is live`() {
        assertEquals(LastSeenFreshness.Live, freshnessAfter(0.seconds))
        assertEquals(LastSeenFreshness.Live, freshnessAfter(15.minutes))
    }

    @Test
    fun `within the last 30 minutes is recent`() {
        assertEquals(LastSeenFreshness.Recent, freshnessAfter(15.minutes + 1.seconds))
        assertEquals(LastSeenFreshness.Recent, freshnessAfter(30.minutes))
    }

    @Test
    fun `older than 30 minutes is stale`() {
        assertEquals(LastSeenFreshness.Stale, freshnessAfter(30.minutes + 1.seconds))
    }

    @Test
    fun `never seen is stale`() {
        assertEquals(
            LastSeenFreshness.Stale,
            lastSeenFreshness(null, lastSeen.toInstant(TimeZone.UTC)),
        )
    }

    @Test
    fun `timestamp slightly in the future because of clock skew is live`() {
        assertEquals(LastSeenFreshness.Live, freshnessAfter((-3).seconds))
    }
}
