package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity

/**
 * A member on the map. [caption] follows the name under the marker, e.g. how old the position is
 */
data class MapPoint(
    val id: String,
    val name: String,
    val coordinates: Coordinates,
    val isOld: Boolean,
    val isSelected: Boolean,
    val activity: UserActivity = UserActivity.Unknown,
    val caption: String? = null,
)
