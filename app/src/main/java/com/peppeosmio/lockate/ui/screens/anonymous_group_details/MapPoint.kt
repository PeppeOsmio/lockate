package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates

data class MapPoint(
    val id: String,
    val name: String,
    val coordinates:Coordinates,
    val isOld: Boolean,
) {
}
