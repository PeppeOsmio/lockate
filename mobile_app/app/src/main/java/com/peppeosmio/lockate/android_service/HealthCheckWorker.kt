package com.peppeosmio.lockate.android_service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.peppeosmio.lockate.service.health.HealthCheck
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Runs the [HealthCheck]; scheduled once by [schedule], WorkManager keeps it across reboots. */
class HealthCheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {
    private val healthCheck: HealthCheck by inject()

    override suspend fun doWork(): Result {
        healthCheck.run()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "health-check"

        /**
         * About every hour, only with a network: without one a server can't be reached anyway, so a
         * day offline doesn't count against it.
         */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<HealthCheckWorker>(1, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
