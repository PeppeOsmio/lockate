package com.peppeosmio.lockate.domain

import com.peppeosmio.lockate.utils.DoubleBytesUtils
import org.maplibre.spatialk.geojson.Position
import java.util.Locale

data class Coordinates(val latitude: Double, val longitude: Double) {
    fun toByteArray(): ByteArray {
        val latitudeArray = DoubleBytesUtils.doubleToByteArray(latitude)
        val longitudeArray = DoubleBytesUtils.doubleToByteArray(longitude)
        return latitudeArray + longitudeArray
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
