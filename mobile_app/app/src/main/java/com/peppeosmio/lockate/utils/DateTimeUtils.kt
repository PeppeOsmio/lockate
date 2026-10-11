package com.peppeosmio.lockate.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DateTimeUtils {
    val DATE_FORMAT = LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()

        char(' ')

        hour()
        char(':')
        minute()
        char(':')
        second()
    }

    @OptIn(ExperimentalTime::class)
    fun utcToCurrentTimeZone(localDateTime: LocalDateTime): LocalDateTime {
        return localDateTime.toInstant(TimeZone.UTC)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }

    @OptIn(ExperimentalTime::class)
    fun formatRelative(past: Instant, now: Instant): String {
        val age = now - past
        return when {
            age < 1.minutes -> "just now"
            age < 1.hours -> "${age.inWholeMinutes} min ago"
            age < 1.days -> "${age.inWholeHours} h ago"
            age < 7.days -> "${age.inWholeDays} d ago"
            else -> past.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        }
    }
}
