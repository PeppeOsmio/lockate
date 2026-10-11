package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.utils.DateTimeUtils
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class LastSeenFreshness { Live, Recent, Stale }

private val LIVE_WINDOW = 1.minutes
private val RECENT_WINDOW = 5.minutes

@OptIn(ExperimentalTime::class)
fun lastSeenFreshness(lastSeen: LocalDateTime?, now: Instant): LastSeenFreshness {
    if (lastSeen == null) return LastSeenFreshness.Stale
    val age = now - lastSeen.toInstant(TimeZone.UTC)
    return when {
        age <= LIVE_WINDOW -> LastSeenFreshness.Live
        age <= RECENT_WINDOW -> LastSeenFreshness.Recent
        else -> LastSeenFreshness.Stale
    }
}

/**
 * Relative "last seen" with a dot that turns green → yellow → grey as the timestamp ages.
 * [lastSeen] is in UTC.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun LastSeenText(modifier: Modifier = Modifier, lastSeen: LocalDateTime?) {
    val now by produceState(Clock.System.now()) {
        while (true) {
            value = Clock.System.now()
            delay(30.seconds)
        }
    }
    val freshness = lastSeenFreshness(lastSeen, now)
    val dotColor = when (freshness) {
        LastSeenFreshness.Live -> Color(0xFF4CAF50)
        LastSeenFreshness.Recent -> Color(0xFFFFC107)
        LastSeenFreshness.Stale -> MaterialTheme.colorScheme.outline
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = when {
                lastSeen == null -> "Never seen"
                freshness == LastSeenFreshness.Live -> "Online"
                else -> "Last seen ${DateTimeUtils.formatRelative(lastSeen.toInstant(TimeZone.UTC), now)}"
            }, style = MaterialTheme.typography.bodyMedium
        )
    }
}
