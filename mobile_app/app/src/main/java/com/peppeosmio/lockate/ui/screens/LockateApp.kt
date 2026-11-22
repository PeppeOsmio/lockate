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
import com.peppeosmio.lockate.ui.routes.CircleEditorRoute
import com.peppeosmio.lockate.ui.routes.CirclesRoute
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.ui.routes.CreateAnonymousGroupRoute
import com.peppeosmio.lockate.ui.routes.HomeRoute
import com.peppeosmio.lockate.ui.routes.InviteRoute
import com.peppeosmio.lockate.ui.routes.JoinAnonymousGroupRoute
import com.peppeosmio.lockate.ui.routes.LoadingRoute
import com.peppeosmio.lockate.ui.routes.ServerScreenMode
import com.peppeosmio.lockate.ui.routes.SettingsRoute
import com.peppeosmio.lockate.ui.screens.circles.CircleEditorScreen
import com.peppeosmio.lockate.ui.screens.circles.CirclesScreen
import com.peppeosmio.lockate.ui.screens.connection_settings.ConnectionSettingsScreen
import com.peppeosmio.lockate.ui.screens.create_anonymous_group.CreateAnonymousGroupScreen
import com.peppeosmio.lockate.ui.screens.invite.InviteScreen
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.JoinAnonymousGroupScreen
import com.peppeosmio.lockate.ui.screens.loading.LoadingScreen
import com.peppeosmio.lockate.ui.screens.map.MapScreen
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
                    navigateToCircles = { anonymousGroupInternalId ->
                        appNavController.navigate(CirclesRoute(anonymousGroupInternalId))
                    },
                    navigateToSettings = { appNavController.navigate(SettingsRoute) },
                    startLocationService = startLocationService,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(navigateBack = { appNavController.popBackStack() })
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
                val joinAnonymousGroupRoute = navBackStackEntry.toRoute<JoinAnonymousGroupRoute>()
                JoinAnonymousGroupScreen(
                    navigateBack = {
                        appNavController.popBackStack()
                    },
                    connectionId = joinAnonymousGroupRoute.connectionSettingsId,
                )
            }
            composable<InviteRoute> { navBackStackEntry ->
                val inviteRoute = navBackStackEntry.toRoute<InviteRoute>()
                InviteScreen(
                    anonymousGroupInternalId = inviteRoute.anonymousGroupInternalId,
                    navigateBack = { appNavController.popBackStack() },
                )
            }
            composable<CirclesRoute> { navBackStackEntry ->
                val circlesRoute = navBackStackEntry.toRoute<CirclesRoute>()
                CirclesScreen(
                    anonymousGroupInternalId = circlesRoute.anonymousGroupInternalId,
                    navigateBack = { appNavController.popBackStack() },
                    navigateToCircleEditor = { circleId ->
                        appNavController.navigate(
                            CircleEditorRoute(
                                anonymousGroupInternalId = circlesRoute.anonymousGroupInternalId,
                                circleId = circleId,
                            )
                        )
                    },
                )
            }
            composable<CircleEditorRoute> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<CircleEditorRoute>()
                CircleEditorScreen(
                    anonymousGroupInternalId = route.anonymousGroupInternalId,
                    circleId = route.circleId,
                    navigateBack = { appNavController.popBackStack() },
                )
            }
            composable<ConnectionSettingsRoute> { navBackStackEntry ->
                val connectionSettingsRoute = navBackStackEntry.toRoute<ConnectionSettingsRoute>()
                ConnectionSettingsScreen(
                    mode = connectionSettingsRoute.mode,
                    connectionId = connectionSettingsRoute.connectionId,
                    navigateBack = {
                        appNavController.popBackStack()
                    },
                    navigateToHome = {
                        appNavController.navigate(HomeRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
