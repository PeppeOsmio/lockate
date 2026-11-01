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

internal const val GOOD_ACCURACY_METERS = 30f
internal const val STALE_FIX_MILLIS = 60_000L
internal const val STILL_TICK_MILLIS = 2_000L
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
 * The positions to send. While the user is still the GPS is released and the last good fix is
 * repeated every [STILL_TICK_MILLIS], so the server can't tell stillness from the messages; without
 * a recent accurate fix the GPS stays on until one arrives, or for at most [STILL_ACQUIRE_MILLIS]
 * if any fix came, since indoors an accurate one may never come. When
 * the user moves again the GPS is back, and the last good fix is held until an accurate one
 * arrives (at most [WARM_UP_MILLIS]). Until the GPS answers after a change of activity, the last
 * good fix is repeated too.
 */
class PositionProvider(
    private val gpsFixes: () -> Flow<LocationFix>,
    private val passiveFixes: () -> Flow<LocationFix>,
    private val activities: () -> Flow<UserActivity>,
    private val now: () -> Long = System::currentTimeMillis,
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
                delay(STILL_TICK_MILLIS)
            }
        })
    }

    private fun movingPositions(activity: UserActivity, resumedFromStill: Boolean): Flow<Position> =
        channelFlow {
            val warmUpEnd = now() + WARM_UP_MILLIS
            // captured now: after a long stillness the first inaccurate fix replaces the stale one
            var held = if (resumedFromStill) lastGoodFix else null
            // starting the GPS takes seconds, and a gap in the messages would tell the server
            // when the user started moving
            val bridge = held ?: lastGoodFix?.takeIf { now() - it.atMillis <= STALE_FIX_MILLIS }
            val bridgeJob = bridge?.let {
                launch {
                    while (true) {
                        send(Position(it, isFresh = false, activity))
                        delay(STILL_TICK_MILLIS)
                    }
                }
            }
            gpsFixes().collect { fix ->
                bridgeJob?.cancel()
                remember(fix)
                if (fix.isAccurate || now() >= warmUpEnd) held = null
                send(held?.let { Position(it, isFresh = false, activity) }
                    ?: Position(fix, isFresh = true, activity))
            }
        }
}
