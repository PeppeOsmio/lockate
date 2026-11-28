package com.peppeosmio.lockate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.peppeosmio.lockate.android_service.AndroidSendLocationService
import com.peppeosmio.lockate.android_service.HealthCheckWorker
import com.peppeosmio.lockate.android_service.HealthNotifier
import com.peppeosmio.lockate.di.appModule
import com.peppeosmio.lockate.di.viewModelModule
import com.peppeosmio.lockate.platform_service.FirebaseSetup
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.push.PushRegistrationService
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class LockateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@LockateApplication)
            modules(
                appModule,
                viewModelModule,
            )
            val channel =
                NotificationChannel(
                    AndroidSendLocationService.NOTIFICATION_CHANNEL_ID,
                    "Location",
                    NotificationManager.IMPORTANCE_LOW,
                )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            // the channel was called "lockateCircles" before places were called places
            notificationManager.deleteNotificationChannel("lockateCircles")
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    AndroidSendLocationService.PLACE_NOTIFICATION_CHANNEL_ID,
                    "Places",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    HealthNotifier.CHANNEL_ID,
                    "Server problems",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        FirebaseSetup.init(this)
        HealthCheckWorker.schedule(this)
        get<PushRegistrationService>().start(get<AnonymousGroupService>().events)
    }
}
