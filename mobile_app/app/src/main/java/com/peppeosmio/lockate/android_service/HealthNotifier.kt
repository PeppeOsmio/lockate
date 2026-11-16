package com.peppeosmio.lockate.android_service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.peppeosmio.lockate.MainActivity
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.service.version.ApiCompatibility

/** The health check's notification, per server, when it doesn't speak this app's API. */
class HealthNotifier(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showIncompatible(connectionId: Long, serverName: String, compatibility: ApiCompatibility) {
        when (compatibility) {
            ApiCompatibility.ServerTooOld ->
                show(
                    incompatibleTag(connectionId),
                    "$serverName needs an update",
                    "It runs an older version of Lockate, so your groups on it aren't updated.",
                )
            ApiCompatibility.AppTooOld ->
                show(
                    incompatibleTag(connectionId),
                    "Update Lockate to use $serverName",
                    "It runs a newer version of Lockate, so your groups on it aren't updated.",
                )
            ApiCompatibility.Compatible -> Unit
        }
    }

    fun clearIncompatible(connectionId: Long) {
        notificationManager.cancel(incompatibleTag(connectionId), 0)
    }

    private fun show(tag: String, title: String, text: String) {
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
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        notificationManager.notify(tag, 0, notification)
    }

    private fun incompatibleTag(connectionId: Long) = "health-api:$connectionId"

    companion object {
        const val CHANNEL_ID = "lockateHealth"
    }
}
