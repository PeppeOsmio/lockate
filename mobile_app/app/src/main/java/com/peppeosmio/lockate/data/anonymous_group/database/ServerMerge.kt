package com.peppeosmio.lockate.data.anonymous_group.database

import com.peppeosmio.lockate.domain.ServerUrl

/** A server as an older database stored it: the address as typed and the API key in clear. */
internal data class StoredServer(
    val id: Long,
    val name: String,
    val url: String,
    val apiKey: String?,
)

/** The servers to keep, and for each server dropped the one its groups move to */
internal data class ServerMerge(val kept: List<StoredServer>, val movedTo: Map<Long, Long>)

/**
 * One server per normalized address: the oldest of the servers saved with the same address stays,
 * with its name and the newest API key any of them had, and takes the groups of the others.
 */
internal fun mergeServers(servers: List<StoredServer>): ServerMerge {
    val kept = mutableListOf<StoredServer>()
    val movedTo = mutableMapOf<Long, Long>()
    servers
        .sortedBy { it.id }
        .groupBy { ServerUrl.normalize(it.url) }
        .forEach { (url, same) ->
            val first = same.first()
            kept += first.copy(url = url, apiKey = same.lastOrNull { it.apiKey != null }?.apiKey)
            same.drop(1).forEach { movedTo[it.id] = first.id }
        }
    return ServerMerge(kept, movedTo)
}
