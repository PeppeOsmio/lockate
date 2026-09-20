package com.peppeosmio.lockate.service

sealed class ConnectionEvent {
    data class ConnectionDeletedEvent(val connectionId: Long) : ConnectionEvent()
}
