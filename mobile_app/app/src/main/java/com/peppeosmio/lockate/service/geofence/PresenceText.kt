package com.peppeosmio.lockate.service.geofence

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** The notification text of a member that stopped or started sharing; [lastSeen] is in UTC. */
fun presenceText(
    memberName: String,
    online: Boolean,
    lastSeen: LocalDateTime?,
    timeZone: TimeZone,
): String {
    if (online) return "$memberName is sharing their location again"
    val local =
        lastSeen?.toInstant(TimeZone.UTC)?.toLocalDateTime(timeZone)
            ?: return "$memberName stopped sharing their location"
    val time = "%02d:%02d".format(local.hour, local.minute)
    return "$memberName stopped sharing their location (last seen $time)"
}
