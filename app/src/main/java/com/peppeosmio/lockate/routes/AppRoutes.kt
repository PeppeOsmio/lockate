package com.peppeosmio.lockate.routes

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route

@Serializable
data object LoadingRoute : Route

@Serializable
data class ConnectionSettingsRoute(val initialConnectionSettingsId: Long?) : Route

@Serializable
data class HomeRoute(val initialConnectionSettingsId: Long) : Route

@Serializable
data class CreateAnonymousGroupRoute(val connectionSettingsId: Long) : Route

@Serializable
data class JoinAnonymousGroupRoute(val connectionSettingsId: Long) : Route

@Serializable
data class AnonymousGroupDetailsRoute(
    val connectionSettingsId: Long, val anonymousGroupId: String, val anonymousGroupName: String
) : Route
