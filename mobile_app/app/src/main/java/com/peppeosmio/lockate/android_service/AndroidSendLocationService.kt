package com.peppeosmio.lockate.android_service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.peppeosmio.lockate.MainActivity
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.notificationText
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.geofence.CIRCLE_NOTIFICATION_TIMEOUT_MILLIS
import com.peppeosmio.lockate.service.geofence.CircleNotification
import com.peppeosmio.lockate.service.geofence.CircleNotificationBook
import com.peppeosmio.lockate.service.geofence.CircleNotificationUpdate
import com.peppeosmio.lockate.utils.SendLoopLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AndroidSendLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val anonymousGroupService: AnonymousGroupService by inject<AnonymousGroupService>()
    private val sendLoopLog: SendLoopLog by inject<SendLoopLog>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // isRunning and the jobs are only touched on the main thread (onStartCommand and the posted
    // notification updates), so a status update arriving after stop() can't repost the notification
    private var isRunning = false
    private var sendJob: Job? = null
    private var notifyJob: Job? = null
    private val circleNotificationBook = CircleNotificationBook(System::currentTimeMillis)
    private var restartJob: Job? = null
    private var notificationBuilder: NotificationCompat.Builder? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendLoopLog.log("service onStartCommand action=${intent?.action} running=$isRunning")
        when (intent?.action) {
            ACTION_START -> start()
            // the system recreated the service after killing the process: a null intent carries no
            // action, so resume sending when we're still allowed to start a location foreground
            // service from the background
            null -> if (hasBackgroundLocationPermission()) start() else stop()
            ACTION_STOP -> stop()
            ACTION_RESTART -> restart()
        }
        return START_STICKY
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun start() {
        // startForeground with type location throws SecurityException when the
        // location permission isn't active, so refuse to start without it.
        if (!hasLocationPermission()) {
            stop()
            return
        }
        isRunning = true
        val builder = notificationBuilder ?: createNotificationBuilder().also {
            notificationBuilder = it
        }
        // also when already running, so the notification is always owned by this instance
        startForeground(1, builder.build())
        if (sendJob?.isActive == true) return

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifyJob?.cancel()
        notifyJob = serviceScope.launch {
            // only the live frames notify, never the ones read back from the server
            anonymousGroupService.groupFrames.collect { groupFrame ->
                val events =
                    anonymousGroupService.describeCircleEvents(groupFrame) ?: return@collect
                mainHandler.post {
                    if (isRunning) showCircleNotifications(circleNotificationBook.record(events))
                }
            }
        }
        sendJob = serviceScope.launch {
            try {
                anonymousGroupService.holdAllChannels { status ->
                    // when the coroutines are canceled the status callback is still invoked
                    // to notify that they're not sending location anymore by decrementing AG count
                    // so update the notification only while running, otherwise a "ghost" notification
                    // is created
                    mainHandler.post {
                        if (isRunning) {
                            notificationManager.notify(
                                1, builder.setContentText(status.notificationText()).build()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showCircleNotifications(update: CircleNotificationUpdate) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            update.member.tag,
            CIRCLE_NOTIFICATION_ID,
            circleNotificationBuilder(update.member, openIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(update.member.lines.joinToString("\n")))
                .build()
        )
        // the summary is silent: the member's notification is the one that alerts
        notificationManager.notify(
            update.summary.tag,
            CIRCLE_NOTIFICATION_ID,
            circleNotificationBuilder(update.summary, openIntent).setGroupSummary(true)
                .setOnlyAlertOnce(true).setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        update.summary.lines.forEach { style.addLine(it) }
                    }).build()
        )
    }

    private fun circleNotificationBuilder(
        notification: CircleNotification, openIntent: PendingIntent
    ) = NotificationCompat.Builder(this, CIRCLE_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(notification.title).setContentText(notification.text)
        .setSmallIcon(R.drawable.ic_launcher_background).setContentIntent(openIntent)
        .setGroup(notification.groupKey).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setWhen(notification.whenMillis).setShowWhen(true)
        .setTimeoutAfter(CIRCLE_NOTIFICATION_TIMEOUT_MILLIS).setAutoCancel(true)

    private fun createNotificationBuilder(): NotificationCompat.Builder {
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle("Lockate")
            .setContentText("Loading...").setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true).addAction(
                R.drawable.outline_stop_24, "Stop", stopPendingIntent
            ).addAction(
                R.drawable.baseline_restart_alt_24, "Restart", restartPendingIntent
            )
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return hasLocationPermission() && checkSelfPermission(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // also cancels the notification explicitly: stopForeground only removes it when this instance
    // is the one that made it foreground, and the system may have recreated the service since
    private fun stop() {
        isRunning = false
        restartJob?.cancel()
        sendJob?.cancel()
        sendJob = null
        notifyJob?.cancel()
        notifyJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(1)
        stopSelf()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun restart() {
        restartJob?.cancel()
        val oldJob = sendJob
        sendJob = null
        notifyJob?.cancel()
        notifyJob = null
        restartJob = serviceScope.launch(Dispatchers.Main) {
            oldJob?.cancelAndJoin()
            start()
        }
    }

    override fun onDestroy() {
        sendLoopLog.log("service onDestroy")
        isRunning = false
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"
        const val NOTIFICATION_CHANNEL_ID = "lockateLocation"
        const val CIRCLE_NOTIFICATION_CHANNEL_ID = "lockateCircles"
        // the notifications of the circles are told apart by their tag
        private const val CIRCLE_NOTIFICATION_ID = 100
    }
}
