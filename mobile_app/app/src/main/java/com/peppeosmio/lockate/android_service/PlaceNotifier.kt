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
import com.peppeosmio.lockate.service.geofence.PLACE_NOTIFICATION_TIMEOUT_MILLIS
import com.peppeosmio.lockate.service.geofence.PlaceEvents
import com.peppeosmio.lockate.service.geofence.PlaceNotification
import com.peppeosmio.lockate.service.geofence.PlaceNotificationBook
import com.peppeosmio.lockate.service.geofence.PlaceNotificationUpdate

// the notifications of the places are told apart by their tag
private const val PLACE_NOTIFICATION_ID = 100

/**
 * Shows the members' arrivals, departures and sharing changes, whether they came on a group socket
 * or in a push: one notification per member that each new event replaces, under one summary per
 * group.
 */
class PlaceNotifier(private val context: Context) {
    private val book = PlaceNotificationBook(System::currentTimeMillis)

    @Synchronized
    fun show(events: PlaceEvents) {
        post(book.record(events))
    }

    private fun post(update: PlaceNotificationUpdate) {
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
            PLACE_NOTIFICATION_ID,
            placeNotificationBuilder(update.member, openIntent)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(update.member.lines.joinToString("\n"))
                )
                .build(),
        )
        // the summary is silent: the member's notification is the one that alerts
        notificationManager.notify(
            update.summary.tag,
            PLACE_NOTIFICATION_ID,
            placeNotificationBuilder(update.summary, openIntent)
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

    private fun placeNotificationBuilder(
        notification: PlaceNotification,
        openIntent: PendingIntent,
    ) =
        NotificationCompat.Builder(
                context,
                AndroidSendLocationService.PLACE_NOTIFICATION_CHANNEL_ID,
            )
            .setContentTitle(notification.title)
            .setContentText(notification.text)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(openIntent)
            .setGroup(notification.groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setWhen(notification.whenMillis)
            .setShowWhen(true)
            .setTimeoutAfter(PLACE_NOTIFICATION_TIMEOUT_MILLIS)
            .setAutoCancel(true)
}
