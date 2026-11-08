package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition

data class QueuedCircleEvent(val transition: CircleTransition, val atMillis: Long)

/**
 * Our circle events waiting for the channel. They carry no time, so the receivers would take a late
 * one as just happened: those older than [MAX_AGE_MILLIS] when the channel is back are dropped.
 */
class CircleEventQueue {
    private val pending = ArrayDeque<QueuedCircleEvent>()

    @Synchronized
    fun add(transition: CircleTransition, atMillis: Long) {
        pending.addLast(QueuedCircleEvent(transition, atMillis))
    }

    /** Takes the events to send at [nowMillis], oldest first. */
    @Synchronized
    fun drain(nowMillis: Long): List<QueuedCircleEvent> =
        pending.filter { nowMillis - it.atMillis < MAX_AGE_MILLIS }.also { pending.clear() }

    /** Returns events [drain] gave that couldn't be sent, ahead of those added since. */
    @Synchronized
    fun putBack(events: List<QueuedCircleEvent>) {
        pending.addAll(0, events)
    }

    companion object {
        const val MAX_AGE_MILLIS = 5 * 60_000L
    }
}
