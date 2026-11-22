package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaLocalDate

/** The days history is kept for, today first, as scrollable chips */
const val HISTORY_DAYS = 7

/** One chip per day of the last [HISTORY_DAYS], from [today] back */
@Composable
fun DayChips(
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (daysAgo in 0 until HISTORY_DAYS) {
            val day = today.minus(DatePeriod(days = daysAgo))
            FilterChip(
                selected = day == selected,
                onClick = { onSelect(day) },
                label = { Text(dayLabel(day, daysAgo)) },
            )
        }
    }
}

internal fun dayLabel(day: LocalDate, daysAgo: Int): String =
    when (daysAgo) {
        0 -> "Today"
        1 -> "Yesterday"
        else ->
            "${day.toJavaLocalDate().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${day.day}"
    }

@ThemePreviews
@Composable
private fun DayChipsPreview() {
    PreviewSurface {
        val today = LocalDate(2026, 11, 22)
        DayChips(
            modifier = Modifier.padding(vertical = 8.dp),
            today = today,
            selected = today.minus(DatePeriod(days = 1)),
            onSelect = {},
        )
    }
}
