package com.peppeosmio.lockate.service.location

import com.peppeosmio.lockate.domain.LocationFix
import com.peppeosmio.lockate.domain.UserActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlin.random.Random

internal const val GOOD_ACCURACY_METERS = 30f
internal const val STALE_FIX_MILLIS = 60_000L
internal const val STILL_INTERVAL_MILLIS = 60_000L
internal const val STILL_JITTER_MILLIS = 2_000L
internal const val WARM_UP_MILLIS = 20_000L
internal const val STILL_ACQUIRE_MILLIS = 20_000L

private val LocationFix.isAccurate get() = accuracyMeters <= GOOD_ACCURACY_METERS

/** The fix to send while still: the newest accurate one, or the best of the last minute */
internal fun pickGoodFix(current: LocationFix?, candidate: LocationFix): LocationFix = when {
    current == null -> candidate
    candidate.isAccurate -> candidate
    candidate.atMillis - current.atMillis > STALE_FIX_MILLIS -> candidate
    candidate.accuracyMeters <= current.accuracyMeters -> candidate
    else -> current
}

/** [isFresh] is false when [fix] is the remembered one and not a new GPS fix */
data class Position(val fix: LocationFix, val isFresh: Boolean, val activity: UserActivity)

/**
 * Waits from [nowMillis] until the next wall-clock minute plus [jitterMillis], skipping a minute
 * that is less than half an interval away, so the heartbeats of a group's still members reach the
 * receivers together and wake their radio once.
 */
internal fun millisToNextStillTick(nowMillis: Long, jitterMillis: Long): Long {
    val next = ((nowMillis + STILL_INTERVAL_MILLIS / 2) / STILL_INTERVAL_MILLIS + 1) * STILL_INTERVAL_MILLIS
    return next + jitterMillis - nowMillis
}

/**
 * The positions to send. While the user is still the GPS is released and the last good fix is
 * repeated about every [STILL_INTERVAL_MILLIS]; without a recent accurate fix the GPS stays on
 * until one arrives, or for at most [STILL_ACQUIRE_MILLIS] if any fix came, since indoors an
 * accurate one may never come. When the user moves again the GPS is back, and the last good fix is
 * held until an accurate one arrives (at most [WARM_UP_MILLIS]). A change of activity is sent at
 * once, with the last good fix if the GPS hasn't answered yet.
 */
class PositionProvider(
    private val gpsFixes: () -> Flow<LocationFix>,
    private val passiveFixes: () -> Flow<LocationFix>,
    private val activities: () -> Flow<UserActivity>,
    private val now: () -> Long = System::currentTimeMillis,
    private val stillJitterMillis: () -> Long = { Random.nextLong(STILL_JITTER_MILLIS) },
) {
    // shared by the groups' send loops, so a group that starts later is still right away
    @Volatile
    private var lastGoodFix: LocationFix? = null

    private fun remember(fix: LocationFix) {
        lastGoodFix = pickGoodFix(lastGoodFix, fix)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun positions(): Flow<Position> = flow {
        var previous: UserActivity? = null
        emitAll(activities().distinctUntilChanged().flatMapLatest { activity ->
            val resumedFromStill = previous == UserActivity.Still
            previous = activity
            if (activity == UserActivity.Still) stillPositions() else movingPositions(
                activity, resumedFromStill
            )
        })
    }

    // a fix from before the send loop stopped may be somewhere else
    private fun hasRecentAccurateFix() =
        lastGoodFix?.let { it.isAccurate && now() - it.atMillis <= STALE_FIX_MILLIS } == true

    private fun stillPositions(): Flow<Position> = flow {
        if (!hasRecentAccurateFix()) {
            val giveUpAt = now() + STILL_ACQUIRE_MILLIS
            emitAll(gpsFixes().transformWhile { fix ->
                remember(fix)
                emit(Position(fix, isFresh = true, UserActivity.Still))
                !fix.isAccurate && now() < giveUpAt
            })
        }
        emitAll(channelFlow {
            launch { passiveFixes().collect { remember(it) } }
            while (true) {
                send(Position(lastGoodFix!!, isFresh = false, UserActivity.Still))
                delay(millisToNextStillTick(now(), stillJitterMillis()))
            }
        })
    }

    private fun movingPositions(activity: UserActivity, resumedFromStill: Boolean): Flow<Position> =
        channelFlow {
            val warmUpEnd = now() + WARM_UP_MILLIS
            // captured now: after a long stillness the first inaccurate fix replaces the stale one
            var held = if (resumedFromStill) lastGoodFix else null
            (held ?: lastGoodFix?.takeIf { now() - it.atMillis <= STALE_FIX_MILLIS })?.let {
                send(Position(it, isFresh = false, activity))
            }
            gpsFixes().collect { fix ->
                remember(fix)
                if (fix.isAccurate || now() >= warmUpEnd) held = null
                send(held?.let { Position(it, isFresh = false, activity) }
                    ?: Position(fix, isFresh = true, activity))
            }
        }
}
