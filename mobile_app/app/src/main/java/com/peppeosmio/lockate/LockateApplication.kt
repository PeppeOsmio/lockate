package com.peppeosmio.lockate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.peppeosmio.lockate.android_service.AndroidSendLocationService
import com.peppeosmio.lockate.di.appModule
import com.peppeosmio.lockate.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class LockateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@LockateApplication)
            modules(
                appModule, viewModelModule
            )
            val channel = NotificationChannel(
                AndroidSendLocationService.NOTIFICATION_CHANNEL_ID,
                "Location",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
