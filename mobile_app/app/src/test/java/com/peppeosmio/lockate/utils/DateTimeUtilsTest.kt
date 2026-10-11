package com.peppeosmio.lockate.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

    @OptIn(ExperimentalTime::class)
    private fun relative(secondsAgo: Long): String {
        val now = Instant.parse("2026-07-12T12:00:00Z")
        return DateTimeUtils.formatRelative(now - secondsAgo.seconds, now)
    }

    @Test
    fun `formatRelative says just now under a minute`() {
        assertEquals("just now", relative(0))
        assertEquals("just now", relative(59))
    }

    @Test
    fun `formatRelative uses minutes under an hour`() {
        assertEquals("1 min ago", relative(60))
        assertEquals("59 min ago", relative(59 * 60 + 59))
    }

    @Test
    fun `formatRelative uses hours under a day`() {
        assertEquals("1 h ago", relative(3600))
        assertEquals("23 h ago", relative(23 * 3600 + 3599))
    }

    @Test
    fun `formatRelative uses days under a week`() {
        assertEquals("1 d ago", relative(24 * 3600))
        assertEquals("6 d ago", relative(6 * 24 * 3600 + 86399))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `formatRelative falls back to the local date after a week`() {
        val now = Instant.parse("2026-07-12T12:00:00Z")
        val past = now - 7.days

        val expected = past.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

        assertEquals(expected, DateTimeUtils.formatRelative(past, now))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `formatRelative treats a timestamp slightly in the future as just now`() {
        val now = Instant.parse("2026-07-12T12:00:00Z")

        assertEquals("just now", DateTimeUtils.formatRelative(now + 5.seconds, now))
    }
}
