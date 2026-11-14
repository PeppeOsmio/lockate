package com.peppeosmio.lockate.android_service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.peppeosmio.lockate.MainActivity
import com.peppeosmio.lockate.R

/** The notification of a server the health check couldn't reach for hours, one per server. */
class HealthNotifier(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun show(connectionId: Long, serverName: String) {
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Can't reach $serverName")
                .setContentText("Your groups on it haven't been updated for hours.")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        notificationManager.notify(tag(connectionId), 0, notification)
    }

    fun clear(connectionId: Long) {
        notificationManager.cancel(tag(connectionId), 0)
    }

    private fun tag(connectionId: Long) = "health:$connectionId"

    companion object {
        const val CHANNEL_ID = "lockateHealth"
    }
}
