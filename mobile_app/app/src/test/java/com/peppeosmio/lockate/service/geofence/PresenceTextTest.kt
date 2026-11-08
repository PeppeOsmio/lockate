package com.peppeosmio.lockate.service.geofence

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceTextTest {
    private val rome = TimeZone.of("Europe/Rome")

    @Test
    fun `going offline tells the last seen time in the local time zone`() {
        assertEquals(
            "Mario stopped sharing their location (last seen 13:03)",
            presenceText(
                "Mario",
                online = false,
                lastSeen = LocalDateTime(2026, 11, 8, 12, 3),
                rome,
            ),
        )
    }

    @Test
    fun `coming back online is described`() {
        assertEquals(
            "Mario is sharing their location again",
            presenceText("Mario", online = true, lastSeen = null, rome),
        )
    }
}
