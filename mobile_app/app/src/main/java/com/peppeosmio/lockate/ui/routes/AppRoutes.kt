package com.peppeosmio.lockate.ui.routes

import kotlinx.serialization.Serializable

@Serializable sealed interface Route

@Serializable data object LoadingRoute : Route

/** The server screen: connecting the first one, adding one, or editing [connectionId] */
@Serializable
enum class ServerScreenMode {
    FirstLaunch,
    Add,
    Edit,
}

/**
 * The server screen. [inviteServer], [inviteApiKey] and [inviteCode] come from an invite's QR code
 * on a server this phone doesn't have yet: adding it then goes on to join.
 */
@Serializable
data class ConnectionSettingsRoute(
    val mode: ServerScreenMode,
    val connectionId: Long? = null,
    val inviteServer: String? = null,
    val inviteApiKey: String? = null,
    val inviteCode: String? = null,
) : Route

@Serializable data object HomeRoute : Route

@Serializable data object SettingsRoute : Route

/** [groupInternalId]: the group to share with once both location permissions are on */
@Serializable data class PermissionsRoute(val groupInternalId: Long? = null) : Route

@Serializable data class CreateAnonymousGroupRoute(val connectionSettingsId: Long) : Route

/** [code] is filled in when the join comes from an invite's QR code */
@Serializable
data class JoinAnonymousGroupRoute(val connectionSettingsId: Long, val code: String? = null) : Route

@Serializable data class InviteRoute(val anonymousGroupInternalId: Long) : Route

@Serializable data class PlacesRoute(val anonymousGroupInternalId: Long) : Route

@Serializable
data class PlaceEditorRoute(
    val anonymousGroupInternalId: Long,
    val placeId: String? = null,
) : Route
