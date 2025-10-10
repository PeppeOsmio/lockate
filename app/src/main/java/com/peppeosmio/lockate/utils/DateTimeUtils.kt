package com.peppeosmio.lockate.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
}