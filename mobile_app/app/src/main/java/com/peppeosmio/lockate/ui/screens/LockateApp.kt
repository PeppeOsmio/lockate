package com.peppeosmio.lockate.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.peppeosmio.lockate.ui.screens.places.PlaceEditorScreen
import com.peppeosmio.lockate.ui.screens.places.PlacesScreen
import com.peppeosmio.lockate.ui.screens.settings.SettingsScreen
import com.peppeosmio.lockate.ui.theme.LockateTheme
import org.koin.compose.koinInject

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
            composable<HomeRoute> {
                MapScreen(
                    navigateToConnectionSettings = { route -> appNavController.navigate(route) },
                    navigateToCreateAG = { connectionSettingsId ->
                        appNavController.navigate(CreateAnonymousGroupRoute(connectionSettingsId))
                    },
                    navigateToJoinAG = { connectionSettingsId ->
                        appNavController.navigate(JoinAnonymousGroupRoute(connectionSettingsId))
                    },
                    navigateToInvite = { anonymousGroupInternalId ->
                        appNavController.navigate(InviteRoute(anonymousGroupInternalId))
                    },
                    navigateToPlaces = { anonymousGroupInternalId ->
                        appNavController.navigate(PlacesRoute(anonymousGroupInternalId))
                    },
                    navigateToSettings = { appNavController.navigate(SettingsRoute) },
                    navigateToPermissions = { groupInternalId ->
                        appNavController.navigate(PermissionsRoute(groupInternalId))
                    },
                    startLocationService = startLocationService,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    navigateBack = { appNavController.popBackStack() },
                    navigateToPermissions = { appNavController.navigate(PermissionsRoute()) },
                )
            }
            composable<PermissionsRoute> { navBackStackEntry ->
                PermissionsScreen(
                    groupInternalId = navBackStackEntry.toRoute<PermissionsRoute>().groupInternalId,
                    navigateBack = { appNavController.popBackStack() },
                )
            }
            composable<CreateAnonymousGroupRoute> { navBackStackEntry ->
                val createAnonymousGroupRoute =
                    navBackStackEntry.toRoute<CreateAnonymousGroupRoute>()
                CreateAnonymousGroupScreen(
                    navigateBack = {
                        appNavController.popBackStack()
                    },
                    connectionSettingsId = createAnonymousGroupRoute.connectionSettingsId,
                )
            }
            composable<JoinAnonymousGroupRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<JoinAnonymousGroupRoute>()
                JoinAnonymousGroupScreen(
                    connectionId = route.connectionSettingsId,
                    code = route.code,
                    navigateBack = { appNavController.popBackStack() },
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
                    navigateBack = { appNavController.popBackStack() },
                )
            }
            composable<PlacesRoute> { navBackStackEntry ->
                val placesRoute = navBackStackEntry.toRoute<PlacesRoute>()
                PlacesScreen(
                    anonymousGroupInternalId = placesRoute.anonymousGroupInternalId,
                    navigateBack = { appNavController.popBackStack() },
                    navigateToPlaceEditor = { placeId ->
                        appNavController.navigate(
                            PlaceEditorRoute(
                                anonymousGroupInternalId = placesRoute.anonymousGroupInternalId,
                                placeId = placeId,
                            )
                        )
                    },
                )
            }
            composable<PlaceEditorRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<PlaceEditorRoute>()
                PlaceEditorScreen(
                    anonymousGroupInternalId = route.anonymousGroupInternalId,
                    placeId = route.placeId,
                    navigateBack = { appNavController.popBackStack() },
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
                    navigateBack = { appNavController.popBackStack() },
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
