package com.peppeosmio.lockate.service.health

/** Notified after this many runs in a row without an answer from a server: about 3 hours. */
const val UNREACHABLE_RUNS_TO_NOTIFY = 3

/**
 * Run about every hour by `HealthCheckWorker`, only with a network: checks that each server with a
 * group of ours still answers and that we are still in each group, and registers our pushes again,
 * so a changed FCM token that `onNewToken` missed is fixed within the hour. A server that hasn't
 * answered for [UNREACHABLE_RUNS_TO_NOTIFY] runs in a row is notified once; its next answer clears
 * the notification.
 */
class HealthCheck(
    private val listGroups: suspend () -> List<Group>,
    private val verify: suspend (Group) -> Outcome,
    private val registerPushes: suspend () -> Unit,
    private val failures: FailureCounts,
    private val notifyUnreachable: suspend (connectionId: Long) -> Unit,
    private val clearUnreachable: suspend (connectionId: Long) -> Unit,
) {
    data class Group(val connectionId: Long, val anonymousGroupInternalId: Long)

    enum class Outcome {
        Ok,
        /**
         * We were removed or the group was deleted: the group is marked, and the server answered.
         */
        Gone,
        Unreachable,
    }

    /** The runs in a row each server didn't answer, kept across runs. */
    interface FailureCounts {
        suspend fun get(connectionId: Long): Int

        suspend fun set(connectionId: Long, count: Int)
    }

    suspend fun run() {
        val groups = listGroups()
        if (groups.isEmpty()) return
        groups
            .groupBy { it.connectionId }
            .forEach { (connectionId, ofServer) ->
                // one answer is enough: the others are checked anyway, so their groups get marked
                val answered = ofServer.map { verify(it) }.any { it != Outcome.Unreachable }
                if (answered) {
                    failures.set(connectionId, 0)
                    clearUnreachable(connectionId)
                } else {
                    val count = failures.get(connectionId) + 1
                    failures.set(connectionId, count)
                    if (count == UNREACHABLE_RUNS_TO_NOTIFY) notifyUnreachable(connectionId)
                }
            }
        registerPushes()
    }
}
