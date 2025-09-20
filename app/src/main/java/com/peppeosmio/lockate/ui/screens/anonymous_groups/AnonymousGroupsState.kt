package com.peppeosmio.lockate.ui.screens.anonymous_groups

import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup

data class AnonymousGroupsState(
    val showLoadingOverlay: Boolean = false,
    val selectedAGIndex: Int? = null,
    val showAddBottomSheet: Boolean = false,
    val showSureLeaveDialog: Boolean = false,

    val anonymousGroups: List<AnonymousGroup>? = null,
)
