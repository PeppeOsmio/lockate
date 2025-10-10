package com.peppeosmio.lockate.service.anonymous_group

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class AnonymousGroupEvent {
    data class NewAnonymousGroupEvent(val anonymousGroupId: String) : AnonymousGroupEvent()
    data class DeleteAnonymousGroupEvent(val anonymousGroupId: String) : AnonymousGroupEvent()
    data class RemovedFromAnonymousGroupEvent(val anonymousGroupId: String) : AnonymousGroupEvent()
    data class ReaddedToAnonymousGroupEvent(val anonymousGroupId: String): AnonymousGroupEvent()
    data class RemoteAGDoesntExistEvent(val anonymousGroupId: String) : AnonymousGroupEvent()
    data class RemoteAGExistsEvent(val anonymousGroupId: String): AnonymousGroupEvent()
    data class AGLocationSentEvent @OptIn(ExperimentalTime::class) constructor(
        val anonymousGroupId: String, val timestamp: Instant
    ) : AnonymousGroupEvent()
}
