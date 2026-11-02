package com.peppeosmio.lockate.platform_service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.utils.SendLoopLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

internal fun userActivityOf(detectedActivityType: Int): UserActivity = when (detectedActivityType) {
    DetectedActivity.STILL -> UserActivity.Still
    DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT -> UserActivity.OnFoot
    DetectedActivity.ON_BICYCLE -> UserActivity.OnBicycle
    DetectedActivity.IN_VEHICLE -> UserActivity.InVehicle
    else -> UserActivity.Unknown
}

/**
 * The user's activity from the Activity Recognition transitions. Stays [UserActivity.Unknown]
 * without the activity recognition permission, so callers behave as if the user were moving.
 */
class ActivityService(
    private val context: Context,
    private val permissionsService: PermissionsService,
    private val sendLoopLog: SendLoopLog,
) {
    private val activity = MutableStateFlow(UserActivity.Unknown)

    @OptIn(ExperimentalAtomicApi::class)
    private val activeCollectors = AtomicInt(0)
    private var receiver: BroadcastReceiver? = null

    private val pendingIntent by lazy {
        // FLAG_MUTABLE: Play Services adds the transition result to the intent
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_TRANSITIONS).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun getActivityUpdates(): Flow<UserActivity> = flow {
        onCollectorAdded()
        try {
            emitAll(activity)
        } finally {
            onCollectorRemoved()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun onCollectorAdded() {
        if (activeCollectors.incrementAndFetch() == 1) {
            startActivityUpdates()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun onCollectorRemoved() {
        if (activeCollectors.decrementAndFetch() == 0) {
            stopActivityUpdates()
        }
    }

    /** Starts the updates that the missing permission didn't allow when the collectors came */
    @OptIn(ExperimentalAtomicApi::class)
    @Synchronized
    fun onPermissionsChanged() {
        if (activeCollectors.load() > 0) startActivityUpdates()
    }

    @Synchronized
    private fun startActivityUpdates() {
        if (receiver != null) return
        if (permissionsService.isPermissionDenied(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sendLoopLog.log("activity updates not started: permission denied")
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val result = intent?.let { ActivityTransitionResult.extractResult(it) } ?: return
                result.transitionEvents.forEach { event ->
                    val eventActivity = userActivityOf(event.activityType)
                    sendLoopLog.log("activity transition type=${event.activityType} transition=${event.transitionType}")
                    when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> activity.value = eventActivity
                        ActivityTransition.ACTIVITY_TRANSITION_EXIT ->
                            activity.compareAndSet(eventActivity, UserActivity.Unknown)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ACTION_TRANSITIONS), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        this.receiver = receiver
        val transitions = TRACKED_ACTIVITIES.flatMap { type ->
            listOf(
                ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                ActivityTransition.ACTIVITY_TRANSITION_EXIT
            ).map {
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(it).build()
            }
        }
        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent)
                .addOnSuccessListener { sendLoopLog.log("activity updates registered") }
                .addOnFailureListener { e ->
                    sendLoopLog.log("activity updates failed ${e::class.simpleName}: ${e.message}")
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun stopActivityUpdates() {
        receiver?.let {
            try {
                ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            context.unregisterReceiver(it)
        }
        receiver = null
        activity.value = UserActivity.Unknown
    }

    companion object {
        private const val ACTION_TRANSITIONS = "com.peppeosmio.lockate.ACTIVITY_TRANSITIONS"
        private val TRACKED_ACTIVITIES = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE
        )
    }
}
