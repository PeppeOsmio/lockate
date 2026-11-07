package com.peppeosmio.lockate.service.geofence

import com.peppeosmio.lockate.domain.anonymous_group.CircleTransition

/**
 * Our circle events waiting for the channel. They carry no time, so the receivers would take a late
 * one as just happened: those older than [MAX_AGE_MILLIS] when the channel is back are dropped.
 */
class CircleEventQueue {
    private val pending = ArrayDeque<Pair<CircleTransition, Long>>()

    @Synchronized
    fun add(transition: CircleTransition, atMillis: Long) {
        pending.addLast(transition to atMillis)
    }

    /** Takes the events to send at [nowMillis], oldest first. */
    @Synchronized
    fun drain(nowMillis: Long): List<CircleTransition> =
        pending.filter { nowMillis - it.second < MAX_AGE_MILLIS }.map { it.first }
            .also { pending.clear() }

    companion object {
        const val MAX_AGE_MILLIS = 5 * 60_000L
    }
}
