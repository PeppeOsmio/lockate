package com.peppeosmio.lockate.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupEvent
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.ui.routes.CreateAnonymousGroupRoute
import com.peppeosmio.lockate.ui.routes.HomeRoute
import com.peppeosmio.lockate.ui.routes.InviteRoute
import com.peppeosmio.lockate.ui.routes.JoinAnonymousGroupRoute
import com.peppeosmio.lockate.ui.routes.LoadingRoute
import com.peppeosmio.lockate.ui.routes.PermissionsRoute
import com.peppeosmio.lockate.ui.routes.PlaceDetailRoute
import com.peppeosmio.lockate.ui.routes.PlaceEditorRoute
import com.peppeosmio.lockate.ui.routes.PlacesRoute
import com.peppeosmio.lockate.ui.routes.ServerScreenMode
import com.peppeosmio.lockate.ui.routes.SettingsRoute
import com.peppeosmio.lockate.ui.screens.connection_settings.ConnectionSettingsScreen
import com.peppeosmio.lockate.ui.screens.create_anonymous_group.CreateAnonymousGroupScreen
import com.peppeosmio.lockate.ui.screens.invite.InviteScreen
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.JoinAnonymousGroupScreen
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.NewServerInvite
import com.peppeosmio.lockate.ui.screens.loading.LoadingScreen
import com.peppeosmio.lockate.ui.screens.map.MapScreen
import com.peppeosmio.lockate.ui.screens.permissions.PermissionsScreen
import com.peppeosmio.lockate.ui.screens.places.PlaceDetailScreen
import com.peppeosmio.lockate.ui.screens.places.PlaceEditorScreen
import com.peppeosmio.lockate.ui.screens.places.PlacesScreen
import com.peppeosmio.lockate.ui.screens.settings.SettingsScreen
import com.peppeosmio.lockate.ui.theme.LockateTheme
import org.koin.compose.koinInject

// a member the map opens on when it comes back to the front, set by the screen that goes back to it
private const val SHOW_MEMBER_KEY = "showMember"

/**
 * Whether this screen is the one on top: a screen on its way out, while the animation runs, is not.
 * A tap there is dropped, like [dropUnlessResumed] does for the taps that take no argument: a
 * second tap on back would otherwise also take away the screen under it (with nothing left the app
 * shows a blank page), and a second tap on a row would open its screen twice.
 */
private fun NavBackStackEntry.isOnTop() = lifecycle.currentState == Lifecycle.State.RESUMED

@Composable
fun LockateApp(startLocationService: () -> Unit) {
    val appNavController = rememberNavController()
    val anonymousGroupService = koinInject<AnonymousGroupService>()

    // The service runs only while we share with some group and stops by itself when we don't, so
    // it is started again whenever a group starts being shared
    LaunchedEffect(Unit) {
        anonymousGroupService.events.collect { event ->
            when (event) {
                is AnonymousGroupEvent.NewAnonymousGroupEvent,
                is AnonymousGroupEvent.ReaddedToAnonymousGroupEvent,
                is AnonymousGroupEvent.RemoteAGExistsEvent,
                is AnonymousGroupEvent.SendEnabledEvent -> startLocationService()
                else -> Unit
            }
        }
    }

    LockateTheme {
        NavHost(
            navController = appNavController,
            startDestination = LoadingRoute,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start)
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End)
            },
        ) {
            composable<LoadingRoute> {
                LoadingScreen(
                    navigateToHome = {
                        appNavController.navigate(HomeRoute) {
                            popUpTo<LoadingRoute>() {
                                inclusive = true
                            }
                        }
                    },
                    navigateToConnectionSettings = {
                        appNavController.navigate(
                            ConnectionSettingsRoute(ServerScreenMode.FirstLaunch)
                        ) {
                            popUpTo<LoadingRoute>() {
                                inclusive = true
                            }
                        }
                    },
                )
            }
            composable<HomeRoute> { navBackStackEntry ->
                val memberToShow by
                    navBackStackEntry.savedStateHandle
                        .getStateFlow<String?>(SHOW_MEMBER_KEY, null)
                        .collectAsState()
                MapScreen(
                    memberToShow = memberToShow,
                    onMemberShown = { navBackStackEntry.savedStateHandle[SHOW_MEMBER_KEY] = null },
                    navigateToConnectionSettings = { route -> appNavController.navigate(route) },
                    navigateToCreateAG = { connectionSettingsId ->
                        if (!navBackStackEntry.isOnTop()) return@MapScreen
                        appNavController.navigate(CreateAnonymousGroupRoute(connectionSettingsId))
                    },
                    navigateToJoinAG = { connectionSettingsId ->
                        if (!navBackStackEntry.isOnTop()) return@MapScreen
                        appNavController.navigate(JoinAnonymousGroupRoute(connectionSettingsId))
                    },
                    navigateToInvite = { anonymousGroupInternalId ->
                        if (!navBackStackEntry.isOnTop()) return@MapScreen
                        appNavController.navigate(InviteRoute(anonymousGroupInternalId))
                    },
                    navigateToPlaces = { anonymousGroupInternalId ->
                        if (!navBackStackEntry.isOnTop()) return@MapScreen
                        appNavController.navigate(PlacesRoute(anonymousGroupInternalId))
                    },
                    navigateToSettings =
                        dropUnlessResumed { appNavController.navigate(SettingsRoute) },
                    navigateToPermissions = { groupInternalId ->
                        appNavController.navigate(PermissionsRoute(groupInternalId))
                    },
                    startLocationService = startLocationService,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                    navigateToPermissions =
                        dropUnlessResumed { appNavController.navigate(PermissionsRoute()) },
                )
            }
            composable<PermissionsRoute> { navBackStackEntry ->
                PermissionsScreen(
                    groupInternalId = navBackStackEntry.toRoute<PermissionsRoute>().groupInternalId,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                )
            }
            composable<CreateAnonymousGroupRoute> { navBackStackEntry ->
                val createAnonymousGroupRoute =
                    navBackStackEntry.toRoute<CreateAnonymousGroupRoute>()
                CreateAnonymousGroupScreen(
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                    connectionSettingsId = createAnonymousGroupRoute.connectionSettingsId,
                )
            }
            composable<JoinAnonymousGroupRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<JoinAnonymousGroupRoute>()
                JoinAnonymousGroupScreen(
                    connectionId = route.connectionSettingsId,
                    code = route.code,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                    navigateToHome = {
                        appNavController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                    },
                    navigateToAddServer = { invite ->
                        appNavController.navigate(
                            ConnectionSettingsRoute(
                                mode = ServerScreenMode.Add,
                                inviteServer = invite.server,
                                inviteApiKey = invite.apiKey,
                                inviteCode = invite.code,
                            )
                        )
                    },
                )
            }
            composable<InviteRoute> { navBackStackEntry ->
                val inviteRoute = navBackStackEntry.toRoute<InviteRoute>()
                InviteScreen(
                    anonymousGroupInternalId = inviteRoute.anonymousGroupInternalId,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                )
            }
            composable<PlacesRoute> { navBackStackEntry ->
                val placesRoute = navBackStackEntry.toRoute<PlacesRoute>()
                PlacesScreen(
                    anonymousGroupInternalId = placesRoute.anonymousGroupInternalId,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                    navigateToNewPlace =
                        dropUnlessResumed {
                            appNavController.navigate(
                                PlaceEditorRoute(placesRoute.anonymousGroupInternalId)
                            )
                        },
                    navigateToPlace = { placeId ->
                        if (!navBackStackEntry.isOnTop()) return@PlacesScreen
                        appNavController.navigate(
                            PlaceDetailRoute(placesRoute.anonymousGroupInternalId, placeId)
                        )
                    },
                )
            }
            composable<PlaceDetailRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<PlaceDetailRoute>()
                PlaceDetailScreen(
                    anonymousGroupInternalId = route.anonymousGroupInternalId,
                    placeId = route.placeId,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                    navigateToEditor =
                        dropUnlessResumed {
                            appNavController.navigate(
                                PlaceEditorRoute(route.anonymousGroupInternalId, route.placeId)
                            )
                        },
                    navigateToMember = { memberId ->
                        if (!navBackStackEntry.isOnTop()) return@PlaceDetailScreen
                        // the map is under the places, on the same group: it opens on the member
                        appNavController
                            .getBackStackEntry<HomeRoute>()
                            .savedStateHandle[SHOW_MEMBER_KEY] = memberId
                        appNavController.popBackStack<HomeRoute>(inclusive = false)
                    },
                )
            }
            composable<PlaceEditorRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<PlaceEditorRoute>()
                PlaceEditorScreen(
                    anonymousGroupInternalId = route.anonymousGroupInternalId,
                    placeId = route.placeId,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                )
            }
            composable<ConnectionSettingsRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<ConnectionSettingsRoute>()
                ConnectionSettingsScreen(
                    mode = route.mode,
                    connectionId = route.connectionId,
                    invite =
                        if (route.inviteServer != null && route.inviteCode != null) {
                            NewServerInvite(
                                route.inviteServer,
                                route.inviteApiKey,
                                route.inviteCode,
                            )
                        } else null,
                    navigateBack = dropUnlessResumed { appNavController.popBackStack() },
                    navigateToHome = {
                        appNavController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                    },
                    navigateToJoin = { connectionId, code ->
                        appNavController.navigate(JoinAnonymousGroupRoute(connectionId, code)) {
                            popUpTo<HomeRoute>()
                        }
                    },
                )
            }
        }
    }
}
