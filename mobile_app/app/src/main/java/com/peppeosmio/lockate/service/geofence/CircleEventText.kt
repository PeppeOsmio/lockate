package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind

/** The notification texts of a member's transitions, skipping the muted circles and members. */
fun circleEventTexts(
    memberId: String,
    memberName: String,
    transitions: List<CircleTransition>,
    circles: Map<String, Circle>,
    mutedTargets: Set<String>
): List<String> {
    if (memberId in mutedTargets) return emptyList()
    return transitions.mapNotNull { transition ->
        val circle = circles[transition.circleId] ?: return@mapNotNull null
        if (circle.id in mutedTargets) return@mapNotNull null
        when (transition.kind) {
            TransitionKind.Enter -> "$memberName arrived at ${circle.name}"
            TransitionKind.Exit -> "$memberName left ${circle.name}"
        }
    }
}
