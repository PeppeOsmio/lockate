package com.peppeosmio.lockate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.peppeosmio.lockate.android_service.AndroidSendLocationService
import com.peppeosmio.lockate.platform_service.ActivityService
import com.peppeosmio.lockate.ui.screens.LockateApp
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val activityService: ActivityService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LockateApp(
                startLocationService = {
                    val intent = Intent(applicationContext, AndroidSendLocationService::class.java)
                    intent.action = AndroidSendLocationService.ACTION_START
                    startForegroundService(intent)
                }
            )
        }
    }

    // the permission may have been granted in the system dialog or in the settings
    override fun onResume() {
        super.onResume()
        activityService.onPermissionsChanged()
    }
}
