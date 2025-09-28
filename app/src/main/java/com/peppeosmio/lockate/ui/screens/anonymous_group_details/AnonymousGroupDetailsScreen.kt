package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import com.peppeosmio.lockate.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.peppeosmio.lockate.service.location.Location
import com.peppeosmio.lockate.ui.composables.SmallCircularProgressIndicator
import dev.sargunv.maplibrecompose.compose.rememberCameraState
import dev.sargunv.maplibrecompose.core.CameraPosition
import io.github.dellisd.spatialk.geojson.Feature as GeoJsonFeature
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.koin.compose.viewmodel.koinViewModel

enum class AnonymousGroupDetailsTab {
    Map, Members, Admin
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnonymousGroupDetailsScreen(
    viewModel: AnonymousGroupDetailsViewModel = koinViewModel(),
    navigateBack: () -> Unit,
    connectionSettingsId: Long,
    anonymousGroupId: String,
    anonymousGroupName: String
) {
    val state by viewModel.state.collectAsState()
    val tabs = AnonymousGroupDetailsTab.entries.toTypedArray()
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val mapCameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                latitude = Location.NAPOLI.latitude, longitude = Location.NAPOLI.longitude
            ), zoom = 15.0
        )
    )

    val geoJsonFeatures = remember(state.membersLocation) {
        if (state.membersLocation == null) {
            null
        } else {
            state.membersLocation!!.map { entry ->
                val member = state.members!!.firstOrNull() { member -> member.id == entry.key }
                    ?: return@map null
                GeoJsonFeature(
                    geometry = Point(
                        coordinates = Position(
                            latitude = entry.value.coordinates.latitude,
                            longitude = entry.value.coordinates.longitude
                        )
                    ), properties = mapOf(
                        "name" to JsonPrimitive(member.name)
                    )
                )
            }.filterNotNull()
        }
    }


    LaunchedEffect(true) {
        viewModel.getInitialDetails(
            anonymousGroupId = anonymousGroupId, connectionSettingsId = connectionSettingsId
        )
    }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            val result = snackbarHostState.showSnackbar(
                message = snackbarMessage.text,
                snackbarMessage.errorDialogInfo?.let { "More" },
                withDismissAction = true
            )
            when (result) {
                SnackbarResult.Dismissed -> Unit
                SnackbarResult.ActionPerformed -> snackbarMessage.errorDialogInfo?.let {
                    viewModel.showErrorDialog(it)
                }
            }
        }
    }

    LaunchedEffect(true) {
        viewModel.mapLocationEvents.collect { location ->
            mapCameraState.animateTo(
                finalPosition = CameraPosition(
                    target = Position(
                        latitude = location.latitude, longitude = location.longitude
                    ), zoom = 15.0
                )
            )
        }
    }

    if (state.showLoadingOverlay) {
        Dialog(onDismissRequest = {}) {
            CircularProgressIndicator()
        }
    }

    state.dialogErrorDialogInfo?.let {
        AlertDialog(title = { Text(it.title) }, text = { Text(it.body) }, dismissButton = {
            TextButton(onClick = { viewModel.hideErrorDialog() }) { Text("Dismiss") }
        }, confirmButton = {}, onDismissRequest = { viewModel.hideErrorDialog() })
    }

    if (state.showDeleteAGDialog) {
        AlertDialog(
            title = { Text("Delete $anonymousGroupName") },
            text = { Text("Are you sure you want do delete this anonymous group? This action is permanent!") },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteAGDialog() }) { Text("No, don't delete") }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val deleted = viewModel.deleteAnonymousGroup(
                            connectionSettingsId = connectionSettingsId,
                            anonymousGroupId = anonymousGroupId
                        )
                        viewModel.hideDeleteAGDialog()
                        if (deleted) {
                            navigateBack()
                        }
                    }
                }) { Text("Yes, delete") }
            },
            onDismissRequest = { viewModel.hideErrorDialog() })
    }

    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }, topBar = {
        TopAppBar(title = {
            Text(
                text = anonymousGroupName,
            )
        }, navigationIcon = {
            IconButton(onClick = {
                navigateBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back"
                )
            }
        }, actions = {
            if (state.reloadData) {
                IconButton(onClick = {
                    coroutineScope.launch {
                        viewModel.remoteOperations(connectionSettingsId = connectionSettingsId)
                    }
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reconnect")
                }
            } else if (state.showLoadingIcon) {
                SmallCircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
            if (state.anonymousGroup != null) {
                Box(
                    modifier = Modifier
                ) {
                    IconButton(onClick = { viewModel.toggleDropdownMenu() }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = state.isDropdownMenuOpen, onDismissRequest = {
                        viewModel.closeDropdownMenu()
                    }) {
                        DropdownMenuItem(leadingIcon = {
                            Checkbox(
                                modifier = Modifier.padding(0.dp),
                                checked = state.anonymousGroup!!.sendLocation,
                                onCheckedChange = null
                            )
                        }, text = { Text("Share location") }, onClick = {
                            coroutineScope.launch {
                                viewModel.toggleAGShareLocation()
                            }
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.outline_logout_24),
                                contentDescription = "Leave anonymous group"
                            )
                        }, text = { Text("Leave") }, onClick = {
                            viewModel.closeDropdownMenu()
                        })

                    }
                }
            }
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Tabs below the TopAppBar
            TabRow(
                modifier = Modifier.fillMaxWidth(),
                selectedTabIndex = pagerState.currentPage,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = pagerState.currentPage == index, onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }, text = { Text(tab.name) })
                }
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                userScrollEnabled = pagerState.currentPage != AnonymousGroupDetailsTab.Map.ordinal,
                // hold all pages in memory, otherwise MapLibre disposes
                // the the layers on the native side but Compose doesn't dispose the Map,
                // meaning we get the Map without layers
                beyondViewportPageCount = tabs.size
            ) { page ->
                when (tabs[page]) {
                    AnonymousGroupDetailsTab.Map -> {
                        MembersMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraState = mapCameraState,
                            features = geoJsonFeatures,
                            onTapMyLocation = {
                                coroutineScope.launch {
                                    viewModel.getCurrentLocation()
                                }
                            })
                    }

                    AnonymousGroupDetailsTab.Members -> {
                        if (state.members == null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            AGMembersList(
                                members = state.members!!,
                                authenticatedMemberId = state.anonymousGroup!!.memberId,
                                onLocateClick = { index ->
                                    coroutineScope.launch {
                                        val success = viewModel.locateMember(index)
                                        if (success) {
                                            pagerState.animateScrollToPage(AnonymousGroupDetailsTab.Map.ordinal)
                                        }
                                    }
                                })
                        }
                    }

                    AnonymousGroupDetailsTab.Admin -> {
                        if (state.isAdminTokenValid == null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            if (state.isAdminTokenValid!!) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    SettingsMenuLink(
                                        title = { Text(text = "Delete anonymous group") },
                                        subtitle = { Text(text = "This action is not reversible!") },
                                        modifier = Modifier,
                                        enabled = true,
                                        action = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete anonymous group"
                                            )
                                        },
                                        onClick = {
                                            viewModel.showDeleteAGDialog()
                                        })
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.Top,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    OutlinedTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = state.adminPasswordText,
                                        onValueChange = { text ->
                                            viewModel.setAdminPasswordText(text)
                                        },
                                        label = { Text("Admin password") })
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Button(onClick = {
                                        coroutineScope.launch {
                                            viewModel.authAdmin(connectionSettingsId)
                                        }
                                    }) { Text("Authenticate") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
