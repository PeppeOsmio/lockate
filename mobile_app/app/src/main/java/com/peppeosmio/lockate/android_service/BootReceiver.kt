package com.peppeosmio.lockate.android_service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // At boot the app is in the background, where the while-in-use location
        // permission is inactive. A location foreground service is only eligible
        // to start here when background location is also granted; otherwise
        // startForeground(location) throws SecurityException and crashes.
        val hasForegroundLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasForegroundLocation || !hasBackgroundLocation) return

        val serviceIntent = Intent(context, AndroidSendLocationService::class.java).apply {
            action = AndroidSendLocationService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
