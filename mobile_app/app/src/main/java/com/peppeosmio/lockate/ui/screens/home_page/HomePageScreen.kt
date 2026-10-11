package com.peppeosmio.lockate.ui.screens.home_page

import com.peppeosmio.lockate.ui.composables.ErrorDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.ui.routes.AnonymousGroupsRoute
import com.peppeosmio.lockate.ui.composables.PermissionsRequester
import com.peppeosmio.lockate.ui.composables.RoundedSearchAppBar
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.ui.screens.anonymous_groups.AnonymousGroupsScreen
import com.peppeosmio.lockate.utils.LockatePermissions
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageScreen(
    navigateToConnectionSettings: (route: ConnectionSettingsRoute) -> Unit,
    navigateToCreateAG: (connectionSettingsId: Long) -> Unit,
    navigateToJoinAG: (connectionSettingsId: Long) -> Unit,
    navigateToAGDetails: (connectionSettingsId: Long, anonymousGroupInternalId: Long, anonymousGroupName: String) -> Unit,
    navigateToManageConnections: () -> Unit,
    startLocationService: () -> Unit,
    stopLocationService: () -> Unit,
    viewModel: HomePageViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    val navController = rememberNavController()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            if (snackbarMessage.errorInfo != null) {
                viewModel.showErrorDialog(snackbarMessage)
            } else {
                snackbarHostState.showSnackbar(
                    message = snackbarMessage.text, withDismissAction = true
                )
            }
        }
    }

    LaunchedEffect(true) {
        viewModel.navigateToConnectionSettingsEvents.collect {
            navigateToConnectionSettings(it)
        }
    }

    // Error dialog
    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }

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

    if (state.isConnectionsDialogOpen && state.connections != null && state.selectedConnectionId != null) {
        ConnectionsDialog(
            connections = state.connections!!.map { it.value },
            selectedConnectionId = state.selectedConnectionId!!,
            onDismiss = viewModel::closeConnectionsMenu,
            onAddNew = {
                viewModel.closeConnectionsMenu()
                navigateToConnectionSettings(
                    ConnectionSettingsRoute(
                        initialConnectionSettingsId = null, showBackButton = true
                    )
                )
            },
            onManage = {
                viewModel.closeConnectionsMenu()
                navigateToManageConnections()
            },
            onSelect = { connectionSettingsId ->
                coroutineScope.launch {
                    viewModel.onConnectionSelected(connectionSettingsId)
                }
                viewModel.closeConnectionsMenu()
            })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { state.registeredOnTapFab?.invoke() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New group") })
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
                        IconButton(onClick = { viewModel.onTapSearchBar() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                actions = {
                    if (state.connections == null) {
                        return@RoundedSearchAppBar
                    }
                    IconButton(onClick = {
                        viewModel.toggleConnectionsMenu()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.outline_data_table_24),
                            contentDescription = "Connections"
                        )
                    }
                },
                onTap = { viewModel.onTapSearchBar() },
                searchPlaceholder = { Text("Search groups") },
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
    ) { innerPadding ->
        if (state.connections == null || state.selectedConnectionId == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            NavHost(
                modifier = Modifier.fillMaxSize().padding(paddingValues = innerPadding),
                navController = navController,
                startDestination = AnonymousGroupsRoute,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }) {
                composable<AnonymousGroupsRoute> {
                    AnonymousGroupsScreen(
                        connectionId = state.selectedConnectionId!!,
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
            }
        }
    }
}
