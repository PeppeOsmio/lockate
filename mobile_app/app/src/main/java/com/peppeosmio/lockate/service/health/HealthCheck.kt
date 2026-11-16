package com.peppeosmio.lockate.service.health

import com.peppeosmio.lockate.service.version.ApiCompatibility

/**
 * Run about every hour by `HealthCheckWorker`, only with a network. For each server with a group of
 * ours it asks which API the server speaks (`GET /api/version`, which also tells whether it
 * answers), then checks that we are still in each of its groups, and finally registers our pushes
 * again, so a changed FCM token that `onNewToken` missed is fixed within the hour.
 *
 * A server that doesn't answer is skipped until the next run, without a notification: it is often
 * only out of reach for a while (a VPN turned off). A server that doesn't speak our API is notified
 * once, and its groups aren't checked: its answers to our API mean nothing, and a 404 would mark
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
    private val notifyIncompatible: suspend (connectionId: Long, ApiCompatibility) -> Unit,
    private val clearIncompatible: suspend (connectionId: Long) -> Unit,
) {
    data class Group(val connectionId: Long, val anonymousGroupInternalId: Long)

    /** Kept across runs, per server. */
    interface State {
        suspend fun incompatibleNotified(connectionId: Long): Boolean

        suspend fun setIncompatibleNotified(connectionId: Long, notified: Boolean)
    }

    suspend fun run() {
        val groups = listGroups()
        if (groups.isEmpty()) return
        groups
            .groupBy { it.connectionId }
            .forEach { (connectionId, ofServer) ->
                val compatibility = compatibility(connectionId) ?: return@forEach
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
