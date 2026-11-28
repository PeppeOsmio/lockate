package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.Place
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

/** What the map's bottom sheet shows */
sealed interface SheetContent {
    data object Loading : SheetContent

    data object NoGroups : SheetContent

    /** The group no longer exists, or this phone's member was removed from it */
    data object GroupGone : SheetContent

    /** Nobody else is in the group yet */
    data object Alone : SheetContent

    /** Sharing on or off, and the members */
    data object Group : SheetContent

    data class Member(val memberId: String) : SheetContent

    /** The camera follows the member; the sheet stays down, the pill on the map says who */
    data class Following(val memberId: String) : SheetContent
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
 * One line about a member: where (a place), how (moving) and how long ago, e.g. "At Home · still ·
 * just now" or "On foot · 1 min ago". A member whose last position is older than the sharing window
 * paused sharing.
 */
@OptIn(ExperimentalTime::class)
fun memberStatus(
    member: AGMember,
    places: List<Place>,
    now: Instant,
): String {
    val record = member.lastLocationRecord ?: return "No location yet"
    val ago = DateTimeUtils.formatRelative(record.timestamp.toInstant(TimeZone.UTC), now)
    if (lastSeenFreshness(record.timestamp, now) == LastSeenFreshness.Stale) {
        return "Paused sharing · $ago"
    }
    val place = places.nearestContaining(record.coordinates)?.let { "At ${it.name}" }
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

/** Who is inside each place, for the places that have someone in them */
fun occupiedPlaces(
    places: List<Place>,
    members: Collection<AGMember>,
): Map<Place, List<AGMember>> =
    members
        .mapNotNull { member ->
            member.lastLocationRecord
                ?.let { places.nearestContaining(it.coordinates) }
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

/** Who is inside a place, this phone's member first, then by name */
internal fun youFirst(inside: List<AGMember>, myMemberId: String): List<AGMember> =
    inside.sortedWith(compareByDescending<AGMember> { it.id == myMemberId }.thenBy { it.name })

sealed interface PlaceDot {
    data class Avatar(val member: AGMember) : PlaceDot

    data class More(val count: Int) : PlaceDot
}

/** Who is inside a place in two dots at most: one or two avatars, or one avatar and "+N" */
internal fun placeDots(inside: List<AGMember>, myMemberId: String): List<PlaceDot> {
    val sorted = youFirst(inside, myMemberId)
    return if (sorted.size <= 2) sorted.map { PlaceDot.Avatar(it) }
    else listOf(PlaceDot.Avatar(sorted.first()), PlaceDot.More(sorted.size - 1))
}

/** Who is inside a place, as one line: "You're here", "Marco and Sara are here" */
internal fun whoIsHere(inside: List<AGMember>, myMemberId: String): String {
    val names = youFirst(inside, myMemberId).map { if (it.id == myMemberId) "You" else it.name }
    return when (names.size) {
        1 -> if (names[0] == "You") "You're here" else "${names[0]} is here"
        2 -> "${names[0]} and ${names[1]} are here"
        else -> "${names[0]}, ${names[1]} and ${names.size - 2} more are here"
    }
}
