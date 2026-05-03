package com.peppeosmio.lockate.utils

import android.Manifest
import android.os.Build
import com.peppeosmio.lockate.ui.composables.PermissionsRequest

object LockatePermissions {
    val PERMISSION_REQUESTS = mutableListOf(
        PermissionsRequest(
            permissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            dialogTitle = "Geolocation permissions required",
            dialogText = "Lockate requires permission to get your position while open to share it with others.",
            dialogGrantButton = "Grant",
            dialogDenyButton = "Deny"
        ), PermissionsRequest(
            permissions = listOf(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ),
            dialogTitle = "Background geolocation permissions required",
            dialogText = "Lockate requires permission to get your position while in the background to share it with others.",
            dialogGrantButton = "Grant",
            dialogDenyButton = "Deny"
        )
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionsRequest(
                    permissions = listOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    dialogTitle = "Notifications permission required",
                    dialogText = "Lockate needs to show notifications to inform you when it's sharing your geolocation.",
                    dialogGrantButton = "Grant",
                    dialogDenyButton = "Deny"
                )
            )
        }
    }.toList()
}