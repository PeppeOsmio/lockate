package com.peppeosmio.lockate.utils

import android.Manifest
import android.os.Build

/**
 * What Lockate asks for, in the order the Permissions screen shows it. Sharing needs the two
 * location permissions; the others are up to the user.
 */
enum class LockatePermission(val manifestPermissions: List<String>, val neededToShare: Boolean) {
    PreciseLocation(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        neededToShare = true,
    ),
    LocationAllTheTime(
        listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        neededToShare = true,
    ),
    Notifications(listOf(Manifest.permission.POST_NOTIFICATIONS), neededToShare = false),
    PhysicalActivity(listOf(Manifest.permission.ACTIVITY_RECOGNITION), neededToShare = false);

    companion object {
        /** Notifications need asking only from Android 13 on */
        val asked: List<LockatePermission> = entries.filter {
            it != Notifications || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }
    }
}
