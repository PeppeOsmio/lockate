package com.peppeosmio.lockate.android_service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
            ACTION_START -> if (!isRunning) start()
            ACTION_STOP -> stop()
            ACTION_RESTART -> if (isRunning) restart()
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
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(this, AndroidSendLocationService::class.java).apply {
            action = ACTION_RESTART
        }
        val restartPendingIntent = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("Lockate")
                .setContentText("Loading...").setSmallIcon(R.drawable.ic_launcher_background)
                .setOngoing(true).addAction(
                    R.drawable.outline_stop_24, "Stop", stopPendingIntent
                ).addAction(
                    R.drawable.baseline_restart_alt_24, "Restart", restartPendingIntent
                )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        serviceScope.launch {
            try {
                anonymousGroupService.sendLocation { activeAGCount ->
                    if (!isRunning) {
                        // when the coroutines are canceled the updateActiveAGCount is called
                        // to notify that they're not sending location anymore by decrementing AG count
                        // so update the notification only if !isRunning, otherwise a "ghost" notification
                        // is created
                        return@sendLocation
                    }
                    val updatedNotification = notification.setContentText(
                        when (activeAGCount) {
                            0 -> "Not sharing location"
                            1 -> "Sharing location with 1 group"
                            else -> "Sharing location with $activeAGCount groups"
                        }
                    )
                    notificationManager.notify(1, updatedNotification.build())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startForeground(1, notification.build())
    }

    private fun stop() {
        isRunning = false
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun restart() {
        stop()
        Handler(Looper.getMainLooper()).postDelayed({
            val startIntent = Intent(this, AndroidSendLocationService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(this, startIntent )
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"
        const val NOTIFICATION_CHANNEL_ID = "lockateLocation"
    }
}