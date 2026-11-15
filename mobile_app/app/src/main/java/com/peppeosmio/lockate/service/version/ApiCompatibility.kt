package com.peppeosmio.lockate.service.version

import com.peppeosmio.lockate.data.version.ServerVersionDto

/** The major of the Lockate API this app speaks: `/api/v1` (docs/versioning.md). */
const val APP_API = 1

enum class ApiCompatibility {
    Compatible,
    ServerTooOld,
    AppTooOld,
}

/**
 * [serverApis] is what the server's `GET /api/version` lists; null when it has no such endpoint,
 * which only servers older than 0.7.0 lack.
 */
fun apiCompatibility(serverApis: List<Int>?): ApiCompatibility =
    when {
        serverApis == null -> ApiCompatibility.ServerTooOld
        APP_API in serverApis -> ApiCompatibility.Compatible
        serverApis.all { it < APP_API } -> ApiCompatibility.ServerTooOld
        else -> ApiCompatibility.AppTooOld
    }

/** What the server list shows about a server's version; [server] is null without `/api/version`. */
fun serverVersionLabel(server: ServerVersionDto?): String =
    when (apiCompatibility(server?.apis)) {
        ApiCompatibility.Compatible -> "Server ${server!!.version}"
        ApiCompatibility.ServerTooOld ->
            server?.let { "Server ${it.version}, needs an update" } ?: "Server needs an update"
        ApiCompatibility.AppTooOld -> "Server ${server!!.version}, needs a newer Lockate"
    }

/** Why a server can't be added, or null when it speaks our API. */
fun incompatibilityMessage(compatibility: ApiCompatibility): String? =
    when (compatibility) {
        ApiCompatibility.Compatible -> null
        ApiCompatibility.ServerTooOld -> "This server needs an update to work with this Lockate"
        ApiCompatibility.AppTooOld -> "This server needs a newer version of Lockate"
    }
