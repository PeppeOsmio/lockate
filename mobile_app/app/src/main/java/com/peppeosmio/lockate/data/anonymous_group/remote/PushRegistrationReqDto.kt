package com.peppeosmio.lockate.data.anonymous_group.remote

import kotlinx.serialization.Serializable

/** `registrationId` and `key` in Base64. */
@Serializable
data class PushRegistrationReqDto(val fcmToken: String, val registrationId: String, val key: String)
