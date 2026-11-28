package com.peppeosmio.lockate.domain.anonymous_group

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.crypto.EncryptedData

data class Place(
    val id: String,
    val name: String,
    val center: Coordinates,
    val radiusMeters: Double,
)

/**
 * The place [point] is inside whose centre is the closest to it, so overlapping places pick one.
 */
fun List<Place>.nearestContaining(point: Coordinates): Place? = mapNotNull { place ->
    val distance = point.distanceMetersTo(place.center)
    if (distance <= place.radiusMeters) place to distance else null
}
    .minByOrNull { it.second }
    ?.first

data class EncryptedPlace(
    val coordinates: EncryptedData,
    val radius: EncryptedData,
    val name: EncryptedData,
)

enum class TransitionKind {
    Enter,
    Exit,
}

data class PlaceTransition(val placeId: String, val kind: TransitionKind)
