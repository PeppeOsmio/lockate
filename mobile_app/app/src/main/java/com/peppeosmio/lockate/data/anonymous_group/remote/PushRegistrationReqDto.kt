package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/** `registrationId` and `key` in Base64. */
@Serializable
data class PushRegistrationReqDto(val fcmToken: String, val registrationId: String, val key: String)

/** Whether the server pushes the group's events: it may run without a push relay. */
@Serializable data class PushRegistrationResDto(val pushes: Boolean)
