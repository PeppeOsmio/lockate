package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.service.location.STILL_JITTER_MILLIS
import com.peppeosmio.lockate.service.location.millisToNextStillTick
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * When the group sockets ping: on the wall-clock minute, like the heartbeats of a still member, so
 * the pings of every group go out in the same radio wakeup as our location. The jitter is the same
 * for all the sockets, and spreads the devices' pings over the server.
 */
class PingClock(
    private val now: () -> Long = System::currentTimeMillis,
    private val jitterMillis: Long = Random.nextLong(STILL_JITTER_MILLIS),
) {
    fun ticks(): Flow<Unit> = flow {
        while (true) {
            delay(millisToNextStillTick(now(), jitterMillis))
            emit(Unit)
        }
    }
}
