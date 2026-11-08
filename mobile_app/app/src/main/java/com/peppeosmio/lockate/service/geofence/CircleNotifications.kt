package com.peppeosmio.lockate.service.geofence

const val CIRCLE_NOTIFICATION_TIMEOUT_MILLIS = 60 * 60 * 1000L

/** The texts of one member's transitions in a group, ready to be notified. */
data class CircleEvents(
    val groupId: String,
    val groupName: String,
    val memberId: String,
    val texts: List<String>,
)

data class CircleNotification(
    val tag: String,
    val groupKey: String,
    val title: String,
    val text: String,
    val lines: List<String>,
    val whenMillis: Long,
)

/** What to post for an event: the member's notification and the summary of its group. */
data class CircleNotificationUpdate(
    val member: CircleNotification,
    val summary: CircleNotification,
)

/**
 * Keeps the last event of every member, so a member has one notification that each new event
 * replaces, and the group has one summary with the last event of each member. Events older than
 * [CIRCLE_NOTIFICATION_TIMEOUT_MILLIS] are forgotten, like the notifications that expire with them.
 */
class CircleNotificationBook(private val now: () -> Long) {
    private class Entry(val texts: List<String>, val atMillis: Long)

    private val groups = mutableMapOf<String, MutableMap<String, Entry>>()

    fun record(events: CircleEvents): CircleNotificationUpdate {
        val at = now()
        forgetExpired(at)
        val members = groups.getOrPut(events.groupId) { mutableMapOf() }
        members[events.memberId] = Entry(events.texts, at)
        val groupKey = "lockate.circles.${events.groupId}"
        val summaryLines = members.values.sortedByDescending { it.atMillis }.map { it.texts.last() }
        return CircleNotificationUpdate(
            member =
                CircleNotification(
                    tag = "circle:${events.groupId}:${events.memberId}",
                    groupKey = groupKey,
                    title = events.groupName,
                    text = events.texts.last(),
                    lines = events.texts,
                    whenMillis = at,
                ),
            summary =
                CircleNotification(
                    tag = "circle-summary:${events.groupId}",
                    groupKey = groupKey,
                    title = events.groupName,
                    text = summaryLines.first(),
                    lines = summaryLines,
                    whenMillis = at,
                ),
        )
    }

    private fun forgetExpired(at: Long) {
        groups.values.forEach { members ->
            members.values.removeAll { at - it.atMillis >= CIRCLE_NOTIFICATION_TIMEOUT_MILLIS }
        }
        groups.values.removeAll { it.isEmpty() }
    }
}
