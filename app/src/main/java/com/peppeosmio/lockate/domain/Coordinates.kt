package com.peppeosmio.lockate.domain

import com.peppeosmio.lockate.utils.DoubleBytesUtils

data class Coordinates(val latitude: Double, val longitude: Double) {
    fun toByteArray(): ByteArray {
        val latitudeArray = DoubleBytesUtils.doubleToByteArray(latitude)
        val longitudeArray = DoubleBytesUtils.doubleToByteArray(longitude)
        return latitudeArray + longitudeArray
    }

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
