package com.peppeosmio.lockate.service.anonymous_group

sealed class AnonymousGroupEvent {
    data class NewAnonymousGroupEvent(val anonymousGroupId: String) : AnonymousGroupEvent()
    data class DeleteAnonymousGroupEvent(val anonymousGroupId: String) : AnonymousGroupEvent()
    data class RemovedFromAnonymousGroupEvent(val anonymousGroupId: String): AnonymousGroupEvent()
    data class RemoteAGDoesntExistEvent(val anonymousGroupId: String): AnonymousGroupEvent()
}
