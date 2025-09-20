package com.peppeosmio.lockate.service

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionsService(private val context: Context) {
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isPermissionDenied(permission: String): Boolean {
        return !isPermissionGranted(permission)
    }
}