package com.peppeosmio.lockate.android_service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AndroidSendLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val anonymousGroupService: AnonymousGroupService by inject<AnonymousGroupService>()
    private var isRunning = false;

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if(!isRunning) start()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun start() {
        isRunning = true
        val stopIntent = Intent(this, AndroidSendLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("Lockate")
                .setContentText("Loading...").setSmallIcon(R.drawable.ic_launcher_background)
                .setOngoing(true)
                .addAction(
                    R.drawable.outline_stop_24,
                    "Stop",
                    stopPendingIntent
                )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        serviceScope.launch {
            try {
                anonymousGroupService.sendLocation { activeAGCount ->
                    val updatedNotification = notification.setContentText(
                        when (activeAGCount) {
                            0 -> "Not sharing location"
                            1 -> "Sharing location with 1 group"
                            else -> "Sharing location with $activeAGCount groups"
                        }
                    )
                    notificationManager.notify(1, updatedNotification.build())
                }
            } catch (e: Exception){
                e.printStackTrace()
            }
        }

        startForeground(1, notification.build())
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_CHANNEL_ID = "lockateLocation"
    }
}