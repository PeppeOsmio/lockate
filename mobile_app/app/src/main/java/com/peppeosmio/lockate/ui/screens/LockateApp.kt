package com.peppeosmio.lockate.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.ui.routes.CreateAnonymousGroupRoute
import com.peppeosmio.lockate.ui.routes.HomeRoute
import com.peppeosmio.lockate.ui.routes.InviteRoute
import com.peppeosmio.lockate.ui.routes.JoinAnonymousGroupRoute
import com.peppeosmio.lockate.ui.routes.LoadingRoute
import com.peppeosmio.lockate.ui.routes.ManageConnectionsRoute
import com.peppeosmio.lockate.ui.routes.SettingsRoute
import com.peppeosmio.lockate.ui.screens.connection_settings.ConnectionSettingsScreen
import com.peppeosmio.lockate.ui.screens.create_anonymous_group.CreateAnonymousGroupScreen
import com.peppeosmio.lockate.ui.screens.invite.InviteScreen
import com.peppeosmio.lockate.ui.screens.map.MapScreen
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.JoinAnonymousGroupScreen
import com.peppeosmio.lockate.ui.screens.loading.LoadingScreen
import com.peppeosmio.lockate.ui.screens.manage_connections.ManageConnectionsScreen
import com.peppeosmio.lockate.ui.screens.settings.SettingsScreen
import com.peppeosmio.lockate.ui.theme.LockateTheme

@Composable
fun LockateApp(startLocationService: () -> Unit, stopLocationService: () -> Unit) {
    val appNavController = rememberNavController()

    LockateTheme {
        NavHost(
            navController = appNavController,
            startDestination = LoadingRoute,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                )
            },
        ) {
            composable<LoadingRoute> {
                LoadingScreen(navigateToHome = {
                    appNavController.navigate(HomeRoute) {
                        popUpTo<LoadingRoute>() {
                            inclusive = true
                        }
                    }
                }, navigateToConnectionSettings = {
                    appNavController.navigate(
                        ConnectionSettingsRoute(
                            initialConnectionSettingsId = null,
                            showBackButton = false
                        )
                    ) {
                        popUpTo<LoadingRoute>() {
                            inclusive = true
                        }
                    }
                })
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
                    navigateToManageConnections = {
                        appNavController.navigate(ManageConnectionsRoute)
                    },
                    navigateToSettings = { appNavController.navigate(SettingsRoute) },
                    startLocationService = startLocationService
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(navigateBack = { appNavController.popBackStack() })
            }
            composable<ManageConnectionsRoute> {
                ManageConnectionsScreen(
                    navigateBack = {
                        appNavController.popBackStack()
                    },
                    navigateToConnectionSettings = { route ->
                        appNavController.navigate(route)
                    })
            }
            composable<CreateAnonymousGroupRoute> { navBackStackEntry ->
                val createAnonymousGroupRoute =
                    navBackStackEntry.toRoute<CreateAnonymousGroupRoute>()
                CreateAnonymousGroupScreen(navigateBack = {
                    appNavController.popBackStack()
                }, connectionSettingsId = createAnonymousGroupRoute.connectionSettingsId)
            }
            composable<JoinAnonymousGroupRoute> { navBackStackEntry ->
                val joinAnonymousGroupRoute = navBackStackEntry.toRoute<JoinAnonymousGroupRoute>()
                JoinAnonymousGroupScreen(navigateBack = {
                    appNavController.popBackStack()
                }, connectionId = joinAnonymousGroupRoute.connectionSettingsId)
            }
            composable<InviteRoute> { navBackStackEntry ->
                val inviteRoute = navBackStackEntry.toRoute<InviteRoute>()
                InviteScreen(
                    anonymousGroupInternalId = inviteRoute.anonymousGroupInternalId,
                    navigateBack = { appNavController.popBackStack() })
            }
            composable<ConnectionSettingsRoute> { navBackStackEntry ->
                val connectionSettingsRoute = navBackStackEntry.toRoute<ConnectionSettingsRoute>()
                ConnectionSettingsScreen(
                    initialConnectionId = connectionSettingsRoute.initialConnectionSettingsId,
                    showBackButton = connectionSettingsRoute.showBackButton,
                    navigateBack = {
                        appNavController.popBackStack()
                    },
                    navigateToHome = {
                        appNavController.navigate(HomeRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                    })
            }
        }
    }
}
