package com.peppeosmio.lockate.platform_service

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.peppeosmio.lockate.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The token this phone receives pushes with, or null when push isn't available. */
fun interface PushTokenSource {
    suspend fun token(): String?
}

/**
 * Firebase is set up by hand from the `lockate.firebase.*` build properties instead of
 * `google-services.json`, so the app builds without a Firebase project and simply has no pushes.
 */
object FirebaseSetup {
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun init(context: Context) {
        if (!isConfigured || FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build(),
        )
    }
}

class FirebasePushTokenSource : PushTokenSource {
    override suspend fun token(): String? {
        if (!FirebaseSetup.isConfigured) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance()
                .token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e ->
                    Log.e("", "No FCM token", e)
                    cont.resume(null)
                }
        }
    }
}
