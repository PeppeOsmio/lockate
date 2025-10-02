package com.peppeosmio.lockate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.peppeosmio.lockate.android_service.AndroidLocationService
import com.peppeosmio.lockate.ui.screens.LockateApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LockateApp(startLocationService = {
                val intent = Intent(applicationContext, AndroidLocationService::class.java)
                intent.action = AndroidLocationService.ACTION_START
                startForegroundService(intent)
            }, stopLocationService = {
                val intent = Intent(applicationContext, AndroidLocationService::class.java)
                intent.action = AndroidLocationService.ACTION_STOP
                startForegroundService(intent)
            })
        }
    }
}
