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

@Serializable
data class ConnectionSettingsRoute(val mode: ServerScreenMode, val connectionId: Long? = null) :
    Route

@Serializable data object HomeRoute : Route

@Serializable data object SettingsRoute : Route

@Serializable data class CreateAnonymousGroupRoute(val connectionSettingsId: Long) : Route

@Serializable data class JoinAnonymousGroupRoute(val connectionSettingsId: Long) : Route

@Serializable data class InviteRoute(val anonymousGroupInternalId: Long) : Route

@Serializable data class CirclesRoute(val anonymousGroupInternalId: Long) : Route

@Serializable
data class CircleEditorRoute(
    val anonymousGroupInternalId: Long,
    val circleId: String? = null,
) : Route
