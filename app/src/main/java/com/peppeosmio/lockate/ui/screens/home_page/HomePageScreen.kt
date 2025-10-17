package com.peppeosmio.lockate.ui.screens.home_page

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.ui.routes.AnonymousGroupsRoute
import com.peppeosmio.lockate.ui.routes.GroupsRoute
import com.peppeosmio.lockate.ui.composables.PermissionsRequester
import com.peppeosmio.lockate.ui.composables.RoundedSearchAppBar
import com.peppeosmio.lockate.ui.screens.anonymous_groups.AnonymousGroupsScreen
import com.peppeosmio.lockate.utils.LockatePermissions
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageScreen(
    navigateToConnectionSettings: (connectionSettingsId: Long?) -> Unit,
    navigateToCreateAG: (connectionSettingsId: Long) -> Unit,
    navigateToJoinAG: (connectionSettingsId: Long) -> Unit,
    navigateToAGDetails: (connectionSettingsId: Long, anonymousGroupInternalId: Long, anonymousGroupName: String) -> Unit,
    startLocationService: () -> Unit,
    stopLocationService: () -> Unit,
    viewModel: HomePageViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    val navController = rememberNavController()

    val drawerState = rememberDrawerState(
        DrawerValue.Closed
    )
    // Current destination for bottom bar selection
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Redirect to settings if needed, should never happen
    LaunchedEffect(state.shouldRedirectToConnectionScreen) {
        if (state.shouldRedirectToConnectionScreen) {
            navigateToConnectionSettings(null)
        }
    }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            val result = snackbarHostState.showSnackbar(
                message = snackbarMessage.text,
                snackbarMessage.errorInfo?.let { "More" },
                withDismissAction = true
            )
            when (result) {
                SnackbarResult.Dismissed -> Unit
                SnackbarResult.ActionPerformed -> snackbarMessage.errorInfo?.let {
                    viewModel.showErrorDialog(it)
                }
            }
        }
    }

    // Error dialog
    state.dialogErrorInfo?.let { error ->
        AlertDialog(title = { Text(error.title) }, text = { Text(error.body) }, dismissButton = {
            TextButton(onClick = { viewModel.hideErrorDialog() }) { Text("Dismiss") }
        }, confirmButton = {}, onDismissRequest = { viewModel.hideErrorDialog() })
    }

    if (state.showLogoutDialog) {
        AlertDialog(
            title = { Text("Disconnect") },
            text = { Text("Do you want to disconnect? You will leave all groups and anonymous groups!") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        if (viewModel.disconnect()) {
                            stopLocationService()
                            navigateToConnectionSettings(null)
                        }
                    }
                }) { Text("Yes, disconnect") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.closeLogoutDialog()
                }) { Text("No, remain connected") }
            },
            onDismissRequest = { viewModel.closeLogoutDialog() })
    }

    if (state.showLoadingOverlay) {
        Dialog(onDismissRequest = {}) {
            CircularProgressIndicator()
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = {})

    PermissionsRequester(
        permissionRequests = LockatePermissions.PERMISSION_REQUESTS,
        requestPermissions = { permissions ->
            multiplePermissionsLauncher.launch(permissions.toTypedArray())
        },
        checkPermissionGranted = { permission ->
            viewModel.checkPermissionGranted(permission)
        },
        onPermissionsRequested = {
            startLocationService()
        })


    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Lockate",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                NavigationDrawerItem(icon = {
                    Icon(
                        painter = painterResource(R.drawable.outline_logout_24),
                        contentDescription = "logout"
                    )
                }, label = { Text("Logout") }, selected = false, onClick = {
                    viewModel.openLogoutDialog()
                    coroutineScope.launch {
                        drawerState.close()
                    }
                })
                NavigationDrawerItem(icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Connection settings"
                    )
                }, label = { Text("Connection settings") }, selected = false, onClick = {
                    navigateToConnectionSettings(state.selectedConnectionId)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                })
            }
        }) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    if (state.registeredOnTapFab != null) {
                        state.registeredOnTapFab!!()
                    }
                }) { Icon(Icons.Default.Add, contentDescription = "Add") }
            },
            topBar = {
                RoundedSearchAppBar(
                    scrollBehavior = scrollBehavior,
                    leadingIcon = {
                        if (state.isSearchBarOpen) {
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                viewModel.closeSearchBar()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        } else {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }

                        }
                    },
                    actions = {
                        if (state.connection == null) {
                            return@RoundedSearchAppBar
                        }
                        Box {
                            IconButton(onClick = {
                                viewModel.toggleConnectionsMenu()
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_data_table_24),
                                    contentDescription = "Connections"
                                )
                            }
                            DropdownMenu(
                                expanded = state.isConnectionsMenuOpen,
                                onDismissRequest = {
                                    viewModel.closeConnectionsMenu()
                                }) {
                                Text(
                                    modifier = Modifier.padding(12.dp),
                                    text = "Connections",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Add new connection") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add new connection"
                                        )
                                    },
                                    onClick = {
                                        viewModel.closeConnectionsMenu()
                                        navigateToConnectionSettings(null)
                                    })
                                state.connection!!.map { (connectionSettingsId, connectionSettings) ->
                                    val connectionName = if (connectionSettings.username != null) {
                                        "${connectionSettings.username}@${connectionSettings.url}"
                                    } else {
                                        connectionSettings.url
                                    }
                                    DropdownMenuItem(
                                        text = { Text(connectionName) },
                                        leadingIcon = {
                                            Checkbox(
                                                modifier = Modifier.padding(0.dp),
                                                checked = state.selectedConnectionId == connectionSettingsId,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            coroutineScope.launch {
                                                viewModel.onConnectionSelected(connectionSettingsId)
                                            }
                                        })
                                }
                            }
                        }
                    },
                    onTap = { viewModel.onTapSearchBar() },
                    searchPlaceholder = { Text("Find anonymous groups") },
                    query = state.searchText,
                    onQueryChange = { query ->
                        viewModel.setSearchText(query)
                        if (state.registeredOnSearch != null) {
                            state.registeredOnSearch!!(query)
                        }
                    },
                    isExpanded = state.isSearchBarOpen
                )
            },
            bottomBar = {
                return@Scaffold
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == AnonymousGroupsRoute::class.qualifiedName,
                        onClick = {
                            if (currentRoute != AnonymousGroupsRoute::class.qualifiedName) {
                                navController.navigate(AnonymousGroupsRoute) {
                                    // Standard bottom-nav behavior
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        label = { Text("Anonymous Groups") })
                    NavigationBarItem(selected = false, onClick = {
                        return@NavigationBarItem
                    }, icon = { Icon(Icons.Default.AccountBox, null) }, label = { Text("Groups") })
                }
            }) { innerPadding ->
            if (state.connection == null || state.selectedConnectionId == null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            } else {
                NavHost(
                    modifier = Modifier.padding(paddingValues = innerPadding),
                    navController = navController,
                    startDestination = AnonymousGroupsRoute,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None }) {
                    composable<AnonymousGroupsRoute> {
                        AnonymousGroupsScreen(
                            connectionSettingsId = state.selectedConnectionId!!,
                            navigateToCreateAG = {
                                navigateToCreateAG(state.selectedConnectionId!!)
                            },
                            navigateToJoinAG = {
                                navigateToJoinAG(state.selectedConnectionId!!)
                            },
                            navigateToAGDetails = { anonymousGroupInternalId, anonymousGroupName ->
                                navigateToAGDetails(
                                    state.selectedConnectionId!!,
                                    anonymousGroupInternalId,
                                    anonymousGroupName
                                )
                            },
                            registerOnFabTap = { onTapFab -> viewModel.registerOnTapFab(onTapFab) },
                            unregisterOnFabTap = { viewModel.unregisterOnTapFab() },
                            registerOnSearch = { onSearch -> viewModel.registerOnSearch(onSearch) },
                            unregisterOnSearch = { viewModel.unregisterOnSearch() },
                            showErrorSnackbar = { errorInfo ->
                                coroutineScope.launch {
                                    viewModel.showSnackbar(errorInfo)
                                }
                            })
                    }
                    composable<GroupsRoute> {}
                }
            }
        }
    }
}
