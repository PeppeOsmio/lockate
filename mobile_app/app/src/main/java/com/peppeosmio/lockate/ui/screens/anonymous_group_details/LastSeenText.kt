package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

enum class LastSeenFreshness {
    Live,
    Recent,
    Stale,
}

// a still phone posts every 10 minutes, and the server says it stopped sharing after 30
private val LIVE_WINDOW = 15.minutes
private val RECENT_WINDOW = 30.minutes

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

/** The dot next to a member's status: green → yellow → grey as their last position ages */
@Composable
fun LastSeenFreshness.dotColor(): Color =
    when (this) {
        LastSeenFreshness.Live -> Color(0xFF4CAF50)
        LastSeenFreshness.Recent -> Color(0xFFFFC107)
        LastSeenFreshness.Stale -> MaterialTheme.colorScheme.outline
    }
