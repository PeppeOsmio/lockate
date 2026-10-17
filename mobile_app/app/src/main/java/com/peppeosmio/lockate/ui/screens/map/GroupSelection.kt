package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup

fun sortGroups(groups: List<AnonymousGroup>): List<AnonymousGroup> =
    groups.sortedBy { it.name.lowercase() }

fun filterGroups(groups: List<AnonymousGroup>, query: String): List<AnonymousGroup> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return groups
    return groups.filter { it.name.contains(trimmed, ignoreCase = true) }
}

fun pickInitialGroup(groups: List<AnonymousGroup>, lastInternalId: Long?): AnonymousGroup? =
    groups.firstOrNull { it.internalId == lastInternalId } ?: sortGroups(groups).firstOrNull()
