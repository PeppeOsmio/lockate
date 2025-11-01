package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.LoadingState


data class AGDetailsState(
    val anonymousGroup: AnonymousGroup? = null,
    val members: Map<String, AGMember>? = null,
    val membersCoordinates: Map<String, Coordinates>? = null,
    val isAdminTokenValid: Boolean? = null,
    val showLoadingOverlay: Boolean = false,
    val adminPasswordText: String = "",
    val showDeleteAGDialog: Boolean = false,
    val dialogErrorInfo: ErrorInfo? = null,
    val remoteDataLoadingState: LoadingState = LoadingState.IsLoading,
    val isDropdownMenuOpen: Boolean = false,
    val myCoordinates: Coordinates? = null,
    val followedMemberId: String? = null
)
