package com.peppeosmio.lockate.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.ExperimentalTime

class DateTimeUtilsTest {

    @Test
    fun `DATE_FORMAT formats a known LocalDateTime as expected`() {
        val localDateTime = LocalDateTime(2026, 7, 12, 14, 30, 5)

        val formatted = DateTimeUtils.DATE_FORMAT.format(localDateTime)

        assertEquals("2026-07-12 14:30:05", formatted)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `utcToCurrentTimeZone converts the UTC-interpreted instant to the system default zone`() {
        val localDateTime = LocalDateTime(2026, 7, 12, 14, 30, 5)
        val expected = localDateTime.toInstant(TimeZone.UTC)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        val actual = DateTimeUtils.utcToCurrentTimeZone(localDateTime)

        assertEquals(expected, actual)
    }
}
