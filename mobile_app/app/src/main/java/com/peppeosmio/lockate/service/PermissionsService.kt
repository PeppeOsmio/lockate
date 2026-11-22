package com.peppeosmio.lockate.service

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.peppeosmio.lockate.utils.LockatePermission

class PermissionsService(private val context: Context) {
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isPermissionDenied(permission: String): Boolean {
        return !isPermissionGranted(permission)
    }

    fun isGranted(permission: LockatePermission): Boolean =
        permission.manifestPermissions.all { isPermissionGranted(it) }

    fun granted(): Map<LockatePermission, Boolean> =
        LockatePermission.asked.associateWith { isGranted(it) }

    fun canShare(): Boolean =
        LockatePermission.asked.filter { it.neededToShare }.all { isGranted(it) }
}
