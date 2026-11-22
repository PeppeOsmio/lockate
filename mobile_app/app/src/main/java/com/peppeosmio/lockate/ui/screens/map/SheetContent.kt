package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.nearestContaining
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsState
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LastSeenFreshness
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.label
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.lastSeenFreshness
import com.peppeosmio.lockate.utils.DateTimeUtils
import java.time.format.TextStyle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.toLocalDateTime

/**
 * What the map's bottom sheet shows. [hasBody]: whether there's more than the header to reveal by
 * dragging the sheet up.
 */
sealed interface SheetContent {
    val hasBody: Boolean

    data object Loading : SheetContent {
        override val hasBody = false
    }

    data object NoGroups : SheetContent {
        override val hasBody = false
    }

    /** The group no longer exists, or this phone's member was removed from it */
    data object GroupGone : SheetContent {
        override val hasBody = false
    }

    /** Nobody else is in the group yet */
    data object Alone : SheetContent {
        override val hasBody = false
    }

    /** Collapsed: sharing on or off. Expanded: members and circles */
    data object Group : SheetContent {
        override val hasBody = true
    }

    data class Member(val memberId: String) : SheetContent {
        override val hasBody = true
    }

    /** The camera follows the member; the sheet shows just who */
    data class Following(val memberId: String) : SheetContent {
        override val hasBody = false
    }
}

fun sheetContent(mapState: MapState, details: AGDetailsState): SheetContent {
    val groups = mapState.groups
    val selectedGroup = mapState.selectedGroup
    val members = details.members
    val selectedMemberId = details.selectedMemberId?.takeIf { members?.containsKey(it) == true }
    return when {
        groups == null || (groups.isNotEmpty() && selectedGroup == null) -> SheetContent.Loading
        groups.isEmpty() -> SheetContent.NoGroups
        !selectedGroup!!.isMember || !selectedGroup.existsRemote -> SheetContent.GroupGone
        members == null || details.anonymousGroup == null -> SheetContent.Loading
        selectedMemberId != null && details.isFollowingSelectedMember ->
            SheetContent.Following(selectedMemberId)
        selectedMemberId != null -> SheetContent.Member(selectedMemberId)
        members.size == 1 -> SheetContent.Alone
        else -> SheetContent.Group
    }
}

/**
 * One line about a member: where (a circle), how (moving) and how long ago, e.g. "At Home · still ·
 * just now" or "On foot · 1 min ago". A member whose last position is older than the sharing window
 * paused sharing.
 */
@OptIn(ExperimentalTime::class)
fun memberStatus(
    member: AGMember,
    circles: List<Circle>,
    now: Instant,
): String {
    val record = member.lastLocationRecord ?: return "No location yet"
    val ago = DateTimeUtils.formatRelative(record.timestamp.toInstant(TimeZone.UTC), now)
    if (lastSeenFreshness(record.timestamp, now) == LastSeenFreshness.Stale) {
        return "Paused sharing · $ago"
    }
    val place = circles.nearestContaining(record.coordinates)?.let { "At ${it.name}" }
    val activity =
        when (record.activity) {
            UserActivity.Unknown -> null
            UserActivity.Still -> if (place != null) "still" else "Still"
            else -> record.activity.label
        }
    return listOfNotNull(place, activity, ago).joinToString(" · ")
}

/** Whether the member counts as sharing now, for the dot next to their status */
@OptIn(ExperimentalTime::class)
fun memberFreshness(member: AGMember, now: Instant): LastSeenFreshness =
    lastSeenFreshness(member.lastLocationRecord?.timestamp, now)

/** Who is inside each circle, for the circles that have someone in them */
fun occupiedCircles(
    circles: List<Circle>,
    members: Collection<AGMember>,
): Map<Circle, List<AGMember>> =
    members
        .mapNotNull { member ->
            member.lastLocationRecord
                ?.let { circles.nearestContaining(it.coordinates) }
                ?.let { it to member }
        }
        .groupBy({ it.first }, { it.second })

/**
 * A UTC time as people say it: "Today at 13:25", "Yesterday at 9:05", "3 Nov at 18:40" (with the
 * year when it isn't this year), in [timeZone].
 */
@OptIn(ExperimentalTime::class)
fun friendlyDateTime(
    utc: LocalDateTime,
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = utc.toInstant(TimeZone.UTC).toLocalDateTime(timeZone)
    val time = "${local.hour}:${local.minute.toString().padStart(2, '0')}"
    return "${dayName(local.date, now, timeZone, capitalized = true)} at $time"
}

/** The day of a UTC time: "today", "yesterday", "3 Nov" or "3 Nov 2025" */
@OptIn(ExperimentalTime::class)
fun friendlyDay(
    utc: LocalDateTime,
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = dayName(utc.toInstant(TimeZone.UTC).toLocalDateTime(timeZone).date, now, timeZone)

@OptIn(ExperimentalTime::class)
private fun dayName(
    day: LocalDate,
    now: Instant,
    timeZone: TimeZone,
    capitalized: Boolean = false,
): String {
    val today = now.toLocalDateTime(timeZone).date
    val name =
        when (day) {
            today -> "today"
            today.minus(DatePeriod(days = 1)) -> "yesterday"
            else -> {
                val month =
                    day.month
                        .toJavaMonth()
                        .getDisplayName(TextStyle.SHORT, java.util.Locale.ENGLISH)
                if (day.year == today.year) "${day.day} $month" else "${day.day} $month ${day.year}"
            }
        }
    return if (capitalized) name.replaceFirstChar { it.uppercase() } else name
}
