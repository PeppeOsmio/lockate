package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.utils.ErrorInfo

data class AnonymousGroupDetailsState(
    val anonymousGroup: AnonymousGroup? = null,
    val members: List<AGMember>? = null,
    val isAdminTokenValid: Boolean? = null,
    val showLoadingOverlay: Boolean = false,
    val showLoadingIcon: Boolean = false,
    val adminPasswordText: String = "",
    val membersLocationRecords: Map<String, LocationRecord>? = null,
    val showDeleteAGDialog: Boolean = false,
    val dialogErrorInfo: ErrorInfo? = null,
    val reloadData: Boolean = false,
    val isDropdownMenuOpen: Boolean = false
)
