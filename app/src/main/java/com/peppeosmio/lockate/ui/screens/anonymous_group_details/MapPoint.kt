package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.Coordinates
import io.github.dellisd.spatialk.geojson.Feature
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.serialization.json.JsonPrimitive

data class MapPoint(
    val name: String,
    val coordinates: Coordinates,
    val isOld: Boolean,
) {
    fun toGeoJsonFeature(): Feature {
        return Feature(
            geometry = Point(
                coordinates = Position(
                    latitude = coordinates.latitude, longitude = coordinates.longitude
                )
            ), properties = mapOf(
                "name" to JsonPrimitive(name),
                "isOld" to JsonPrimitive(isOld),
            )
        )
    }
}
