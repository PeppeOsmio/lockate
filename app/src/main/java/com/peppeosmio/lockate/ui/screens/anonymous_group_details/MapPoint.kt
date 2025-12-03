package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates
import io.github.dellisd.spatialk.geojson.Feature
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.serialization.json.JsonPrimitive

data class MapPoint(
    val id: String,
    val name: String,
    val coordinates: Coordinates,
    val isOld: Boolean,
) {
}
