package com.peppeosmio.lockate.android_service

import com.google.firebase.messaging.FirebaseMessagingService
import com.peppeosmio.lockate.service.push.PushRegistrationService
import org.koin.android.ext.android.inject

/** Gets the pushes the servers send through the relay. */
class LockateMessagingService : FirebaseMessagingService() {
    private val pushRegistrationService: PushRegistrationService by inject()

    override fun onNewToken(token: String) {
        pushRegistrationService.refreshAll()
    }
}
