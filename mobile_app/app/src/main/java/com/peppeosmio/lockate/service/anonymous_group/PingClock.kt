package com.peppeosmio.lockate.service.anonymous_group

import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal const val PING_INTERVAL_MILLIS = 60_000L
internal const val PING_JITTER_MILLIS = 2_000L

/**
 * Waits from [nowMillis] until the next wall-clock minute plus [jitterMillis], skipping a minute
 * that is less than half an interval away.
 */
internal fun millisToNextTick(nowMillis: Long, jitterMillis: Long): Long {
    val next =
        ((nowMillis + PING_INTERVAL_MILLIS / 2) / PING_INTERVAL_MILLIS + 1) * PING_INTERVAL_MILLIS
    return next + jitterMillis - nowMillis
}

/**
 * When the group sockets ping: on the wall-clock minute, so the pings of every group go out in the
 * same radio wakeup. The jitter is the same for all the sockets, and spreads the devices' pings
 * over the server.
 */
class PingClock(
    private val now: () -> Long = System::currentTimeMillis,
    private val jitterMillis: Long = Random.nextLong(PING_JITTER_MILLIS),
) {
    fun ticks(): Flow<Unit> = flow {
        while (true) {
            delay(millisToNextTick(now(), jitterMillis))
            emit(Unit)
        }
    }
}
