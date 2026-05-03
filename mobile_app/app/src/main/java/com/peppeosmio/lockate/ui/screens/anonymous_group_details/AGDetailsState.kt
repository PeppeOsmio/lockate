package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.LoadingState


data class AGDetailsState(
    val anonymousGroup: AnonymousGroup? = null,
    val members: Map<String, AGMember>? = null,
    val membersCoordinates: Map<String, Coordinates>? = null,
    val showLoadingOverlay: Boolean = false,
    val adminPasswordText: String = "",
    val showDeleteAGDialog: Boolean = false,
    val dialogErrorInfo: ErrorInfo? = null,
    val remoteDataLoadingState: LoadingState = LoadingState.IsLoading,
    val isDropdownMenuOpen: Boolean = false,
    // this is LocationRecord and not Coordinates so that we can understand if the
    // coordinates are newer even if they are equal to the previous ones
    val myLocationRecordFromGPS: LocationRecord? = null,
    val myDeviceOrientation: Float? = null,
    val followedMemberId: String? = null
)
