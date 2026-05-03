package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.domain.LocationRecord
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class AnonymousGroupEvent {
    data class NewAnonymousGroupEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class DeleteAnonymousGroupEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class RemovedFromAnonymousGroupEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class ReaddedToAnonymousGroupEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class RemoteAGDoesntExistEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class RemoteAGExistsEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class SendDisabledEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class SendEnabledEvent(
        val anonymousGroupInternalId: Long, val anonymousGroupId: String, val connectionId: Long
    ) : AnonymousGroupEvent()

    data class AGLocationSentEvent(
        val anonymousGroupInternalId: Long,
        val anonymousGroupId: String,
        val connectionId: Long,
        val locationRecord: LocationRecord
    ) : AnonymousGroupEvent()
}
