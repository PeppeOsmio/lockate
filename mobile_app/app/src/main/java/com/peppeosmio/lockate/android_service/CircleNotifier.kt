package com.peppeosmio.lockate.android_service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.peppeosmio.lockate.MainActivity
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.service.geofence.CIRCLE_NOTIFICATION_TIMEOUT_MILLIS
import com.peppeosmio.lockate.service.geofence.CircleEvents
import com.peppeosmio.lockate.service.geofence.CircleNotification
import com.peppeosmio.lockate.service.geofence.CircleNotificationBook
import com.peppeosmio.lockate.service.geofence.CircleNotificationUpdate

// the notifications of the circles are told apart by their tag
private const val CIRCLE_NOTIFICATION_ID = 100

/**
 * Shows the members' arrivals, departures and sharing changes, whether they came on a group socket
 * or in a push: one notification per member that each new event replaces, under one summary per
 * group.
 */
class CircleNotifier(private val context: Context) {
    private val book = CircleNotificationBook(System::currentTimeMillis)

    @Synchronized
    fun show(events: CircleEvents) {
        post(book.record(events))
    }

    private fun post(update: CircleNotificationUpdate) {
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        )
            return
        val openIntent =
            PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            update.member.tag,
            CIRCLE_NOTIFICATION_ID,
            circleNotificationBuilder(update.member, openIntent)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(update.member.lines.joinToString("\n"))
                )
                .build(),
        )
        // the summary is silent: the member's notification is the one that alerts
        notificationManager.notify(
            update.summary.tag,
            CIRCLE_NOTIFICATION_ID,
            circleNotificationBuilder(update.summary, openIntent)
                .setGroupSummary(true)
                .setOnlyAlertOnce(true)
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        update.summary.lines.forEach { style.addLine(it) }
                    }
                )
                .build(),
        )
    }

    private fun circleNotificationBuilder(
        notification: CircleNotification,
        openIntent: PendingIntent,
    ) =
        NotificationCompat.Builder(
                context,
                AndroidSendLocationService.CIRCLE_NOTIFICATION_CHANNEL_ID,
            )
            .setContentTitle(notification.title)
            .setContentText(notification.text)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(openIntent)
            .setGroup(notification.groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setWhen(notification.whenMillis)
            .setShowWhen(true)
            .setTimeoutAfter(CIRCLE_NOTIFICATION_TIMEOUT_MILLIS)
            .setAutoCancel(true)
}
