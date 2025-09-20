package com.peppeosmio.lockate.service.location

import android.util.Log
import com.peppeosmio.lockate.utils.DoubleBytesUtils
import dev.whyoleg.cryptography.bigint.encodeToByteArray
import dev.whyoleg.cryptography.bigint.toBigInt

data class Location(val latitude: Double, val longitude: Double) {
    fun toByteArray(): ByteArray {
        val latitudeArray = DoubleBytesUtils.doubleToByteArray(latitude)
        val longitudeArray = DoubleBytesUtils.doubleToByteArray(longitude)
        return latitudeArray + longitudeArray
    }

    companion object {
        val NAPOLI = Location(latitude = 40.8517746, longitude = 14.2681244)
    }
}
