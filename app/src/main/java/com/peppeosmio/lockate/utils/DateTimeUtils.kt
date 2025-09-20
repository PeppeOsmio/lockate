package com.peppeosmio.lockate.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char

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
}