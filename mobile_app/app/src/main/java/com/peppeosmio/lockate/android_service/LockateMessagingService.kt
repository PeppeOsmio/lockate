package com.peppeosmio.lockate.android_service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.push.PushRegistrationService
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/** Gets the pushes the servers send through the relay. */
class LockateMessagingService : FirebaseMessagingService() {
    private val pushRegistrationService: PushRegistrationService by inject()
    private val anonymousGroupService: AnonymousGroupService by inject()
    private val placeNotifier: PlaceNotifier by inject()

    // FCM calls this on a background thread and keeps the app awake until it returns
    override fun onMessageReceived(message: RemoteMessage) {
        runBlocking { anonymousGroupService.openPush(message.data) }?.let { placeNotifier.show(it) }
    }

    override fun onNewToken(token: String) {
        pushRegistrationService.refreshAll()
    }
}
