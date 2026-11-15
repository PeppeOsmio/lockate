package com.peppeosmio.lockate.service.health

import com.peppeosmio.lockate.service.version.ApiCompatibility

/** Notified after this many runs in a row without an answer from a server: about 3 hours. */
const val UNREACHABLE_RUNS_TO_NOTIFY = 3

/**
 * Run about every hour by `HealthCheckWorker`, only with a network. For each server with a group of
 * ours it asks which API the server speaks (`GET /api/version`, which also tells whether it
 * answers), then checks that we are still in each of its groups, and finally registers our pushes
 * again, so a changed FCM token that `onNewToken` missed is fixed within the hour.
 *
 * A server that hasn't answered for [UNREACHABLE_RUNS_TO_NOTIFY] runs in a row is notified once;
 * its next answer clears the notification. A server that doesn't speak our API is notified once
 * too, and its groups aren't checked: its answers to our API mean nothing, and a 404 would mark
 * them deleted.
 */
class HealthCheck(
    private val listGroups: suspend () -> List<Group>,
    /** Null when the server didn't answer. */
    private val compatibility: suspend (connectionId: Long) -> ApiCompatibility?,
    /** Marks the group when we were removed or it was deleted. */
    private val verify: suspend (Group) -> Unit,
    private val registerPushes: suspend () -> Unit,
    private val state: State,
    private val notifyUnreachable: suspend (connectionId: Long) -> Unit,
    private val clearUnreachable: suspend (connectionId: Long) -> Unit,
    private val notifyIncompatible: suspend (connectionId: Long, ApiCompatibility) -> Unit,
    private val clearIncompatible: suspend (connectionId: Long) -> Unit,
) {
    data class Group(val connectionId: Long, val anonymousGroupInternalId: Long)

    /** Kept across runs, per server. */
    interface State {
        /** The runs in a row the server didn't answer. */
        suspend fun failures(connectionId: Long): Int

        suspend fun setFailures(connectionId: Long, count: Int)

        suspend fun incompatibleNotified(connectionId: Long): Boolean

        suspend fun setIncompatibleNotified(connectionId: Long, notified: Boolean)
    }

    suspend fun run() {
        val groups = listGroups()
        if (groups.isEmpty()) return
        groups
            .groupBy { it.connectionId }
            .forEach { (connectionId, ofServer) ->
                val compatibility = compatibility(connectionId)
                if (compatibility == null) {
                    val count = state.failures(connectionId) + 1
                    state.setFailures(connectionId, count)
                    if (count == UNREACHABLE_RUNS_TO_NOTIFY) notifyUnreachable(connectionId)
                    return@forEach
                }
                state.setFailures(connectionId, 0)
                clearUnreachable(connectionId)
                if (compatibility != ApiCompatibility.Compatible) {
                    if (!state.incompatibleNotified(connectionId)) {
                        state.setIncompatibleNotified(connectionId, true)
                        notifyIncompatible(connectionId, compatibility)
                    }
                    return@forEach
                }
                if (state.incompatibleNotified(connectionId)) {
                    state.setIncompatibleNotified(connectionId, false)
                    clearIncompatible(connectionId)
                }
                ofServer.forEach { verify(it) }
            }
        registerPushes()
    }
}
