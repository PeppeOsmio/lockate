package com.peppeosmio.lockate.domain

import com.peppeosmio.lockate.utils.DoubleBytesUtils
import org.maplibre.spatialk.geojson.Position
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinates(val latitude: Double, val longitude: Double) {
    fun toByteArray(): ByteArray {
        val latitudeArray = DoubleBytesUtils.doubleToByteArray(latitude)
        val longitudeArray = DoubleBytesUtils.doubleToByteArray(longitude)
        return latitudeArray + longitudeArray
    }

    /** Great-circle distance in meters (haversine) */
    fun distanceMetersTo(other: Coordinates): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    fun toMapLibreComposePosition() : Position {
        return Position(longitude = longitude, latitude = latitude)
    }

    // Locale.ROOT keeps the dot separator so maps apps can parse the pasted "lat,lng"
    fun toShareableString(): String =
        String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

    // The parenthesised label makes maps apps treat q as a pin instead of a search string
    fun toGeoUri(): String = toShareableString().let { "geo:$it?q=$it(Location)" }

    companion object {
        const val EARTH_RADIUS_METERS = 6_371_008.8

        val NAPOLI = Coordinates(latitude = 40.8517746, longitude = 14.2681244)

        fun fromByteArray(bytes: ByteArray): Coordinates {
            val latitude =
                DoubleBytesUtils.byteArrayToDouble(bytes.copyOfRange(0, 8))
            val longitude =
                DoubleBytesUtils.byteArrayToDouble(bytes.copyOfRange(8, 16))
            return Coordinates(latitude = latitude, longitude = longitude)
        }
    }
}
