package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.domain.anonymous_group.PlaceTransition
import com.peppeosmio.lockate.domain.anonymous_group.TransitionKind

/** The notification texts of a member's transitions, skipping the muted places and members. */
fun placeEventTexts(
    memberId: String,
    memberName: String,
    transitions: List<PlaceTransition>,
    places: Map<String, Place>,
    mutedTargets: Set<String>,
): List<String> {
    if (memberId in mutedTargets) return emptyList()
    return transitions.mapNotNull { transition ->
        val place = places[transition.placeId] ?: return@mapNotNull null
        if (place.id in mutedTargets) return@mapNotNull null
        when (transition.kind) {
            TransitionKind.Enter -> "$memberName arrived at ${place.name}"
            TransitionKind.Exit -> "$memberName left ${place.name}"
        }
    }
}
