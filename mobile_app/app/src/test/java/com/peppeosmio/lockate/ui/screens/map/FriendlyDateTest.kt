package com.peppeosmio.lockate.ui.screens.map

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTime::class)
class FriendlyDateTest {
    private val rome = TimeZone.of("Europe/Rome")
    private val now = Instant.parse("2026-11-22T13:42:00Z")

    @Test
    fun `today and yesterday by name, in the local time zone`() {
        assertEquals(
            "Today at 14:25",
            friendlyDateTime(LocalDateTime(2026, 11, 22, 13, 25), now, rome),
        )
        assertEquals(
            "Yesterday at 9:05",
            friendlyDateTime(LocalDateTime(2026, 11, 21, 8, 5), now, rome),
        )
    }

    @Test
    fun `older days by date, with the year only when it isn't this one`() {
        assertEquals(
            "3 Nov at 19:40",
            friendlyDateTime(LocalDateTime(2026, 11, 3, 18, 40), now, rome),
        )
        assertEquals("today", friendlyDay(LocalDateTime(2026, 11, 22, 0, 30), now, rome))
        assertEquals("3 Nov 2025", friendlyDay(LocalDateTime(2025, 11, 3, 12, 0), now, rome))
    }
}
