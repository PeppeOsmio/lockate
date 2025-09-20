package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.anonymous_group.AGLocation
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.service.location.Location
import com.peppeosmio.lockate.utils.ErrorDialogInfo

data class AnonymousGroupDetailsState(
    val anonymousGroup: AnonymousGroup? = null,
    val members: List<AGMember>? = null,
    val isAdminTokenValid: Boolean? = null,
    val showLoadingOverlay: Boolean = false,
    val showLoadingIcon: Boolean = false,
    val adminPasswordText: String = "",
    val membersLocation: Map<String, AGLocation>? = null,
    val showDeleteAGDialog: Boolean = false,
    val dialogErrorDialogInfo: ErrorDialogInfo? = null,
    val reloadData: Boolean = false,
    val isDropdownMenuOpen: Boolean = false
)
