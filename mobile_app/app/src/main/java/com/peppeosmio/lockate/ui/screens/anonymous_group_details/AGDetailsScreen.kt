package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.ui.composables.SmallCircularProgressIndicator
import com.peppeosmio.lockate.utils.LoadingState
import dev.sargunv.maplibrecompose.compose.rememberCameraState
import dev.sargunv.maplibrecompose.core.CameraPosition
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun AnonymousGroupDetailsScreen(
    viewModel: AGDetailsViewModel = koinViewModel(),
    navigateBack: () -> Unit,
    connectionId: Long,
    anonymousGroupInternalId: Long,
    anonymousGroupName: String
) {
    val state by viewModel.state.collectAsState()
    val tabs = AGDetailsViewModel.AGDetailsTab.entries.toTypedArray()
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    var oldLocations by remember { mutableStateOf(emptySet<String>()) }
    val oldLocationsJobs = remember { emptyMap<String, Job>() }.toMutableMap()
    var isMyLocationOld by remember { mutableStateOf(false) }
    var isMyLocationOldJob = remember<Job?> { null }

    val snackbarHostState = remember { SnackbarHostState() }
    val mapCameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                latitude = Coordinates.NAPOLI.latitude, longitude = Coordinates.NAPOLI.longitude
            ), zoom = 15.0
        )
    )

    LaunchedEffect(true) {
        viewModel.navigateBackEvents.collect {
            navigateBack()
        }
    }

    LaunchedEffect(true) {
        viewModel.pagerEvents.collect { page ->
            pagerState.animateScrollToPage(page.ordinal)
        }
    }

    val membersPoints =
        remember(state.members, oldLocations, state.anonymousGroup) {
            if (state.anonymousGroup == null) {
                null
            } else {
                state.members?.map { (memberId, member) ->
                    if (state.anonymousGroup!!.memberId == memberId) {
                        return@map null
                    } else {
                        val locationRecord = member.lastLocationRecord ?: return@map null
                        MapPoint(
                            coordinates = locationRecord.coordinates,
                            name = member.name,
                            isOld = oldLocations.contains(memberId),
                            id = memberId
                        )
                    }
                }?.filterNotNull()
            }
        }

    val myPoint = remember(state.myLocationRecordFromGPS, state.anonymousGroup, isMyLocationOld) {
        if (state.myLocationRecordFromGPS == null || state.anonymousGroup == null) {
            return@remember null
        }
        MapPoint(
            coordinates = state.myLocationRecordFromGPS!!.coordinates,
            name = "You",
            isOld = isMyLocationOld,
            id = state.anonymousGroup!!.memberId
        )
    }

    LaunchedEffect(state.members) {
        state.members?.forEach { (memberId, member) ->
            oldLocations -= member.id
            oldLocationsJobs[member.id]?.cancel()
            if (member.lastLocationRecord == null) {
                return@forEach
            }
            val now = Clock.System.now()
            val locationTimestamp = member.lastLocationRecord.timestamp.toInstant(TimeZone.UTC)
            var msToWait = (locationTimestamp.plus(1.minutes) - now).inWholeMilliseconds
            if (msToWait < 0) {
                msToWait = 0
            }
            oldLocationsJobs += member.id to launch {
                delay(msToWait)
                if (state.members!![memberId]?.lastLocationRecord?.timestamp == member.lastLocationRecord.timestamp) {
                    oldLocations += memberId
                }
            }
        }
    }

    LaunchedEffect(state.myLocationRecordFromGPS) {
        state.myLocationRecordFromGPS?.let {
            isMyLocationOldJob?.cancel()
            isMyLocationOld = false
            isMyLocationOldJob = launch {
                delay(60000L)
                isMyLocationOld = true
            }
        }
    }


    LaunchedEffect(true) {
        viewModel.getInitialDetails(
            anonymousGroupInternalId = anonymousGroupInternalId, connectionSettings = connectionId
        )
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

    LaunchedEffect(true) {
        viewModel.cameraPositionEvents.collect { location ->
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

    state.dialogErrorInfo?.let {
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
                    viewModel.deleteAnonymousGroup(
                        connectionSettingsId = connectionId,
                        anonymousGroupInternalId = anonymousGroupInternalId
                    )
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
            when (state.remoteDataLoadingState) {
                LoadingState.Failed -> {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.remoteOperations(connectionSettingsId = connectionId)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reconnect")
                    }
                }

                LoadingState.IsLoading -> {
                    SmallCircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }

                else -> Unit
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
                            viewModel.toggleAGShareLocation()
                        })
                        DropdownMenuItem(leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.outline_logout_24),
                                contentDescription = "Leave anonymous group"
                            )
                        }, text = { Text("Leave") }, onClick = {
                            viewModel.closeDropdownMenu()
                        })
                        state.anonymousGroup?.let {
                            if(it.memberIsAGAdmin) {
                                DropdownMenuItem(leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete anonymous group"
                                    )
                                }, text = { Text("Delete") }, onClick = {
                                    viewModel.showDeleteAGDialog()
                                })
                            }
                        }

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
                userScrollEnabled = pagerState.currentPage != AGDetailsViewModel.AGDetailsTab.Map.ordinal,
                // hold all pages in memory, otherwise MapLibre disposes
                // the the layers on the native side but Compose doesn't dispose the Map,
                // meaning we get the Map without layers
                beyondViewportPageCount = tabs.size
            ) { page ->
                when (tabs[page]) {
                    AGDetailsViewModel.AGDetailsTab.Map -> {
                        Box {
                            MembersMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraState = mapCameraState,
                                membersPoints = membersPoints,
                                myPoint = myPoint,
                                myDeviceOrientation = state.myDeviceOrientation,
                                onTapMyLocation = {
                                    viewModel.onTapMyLocation()
                                })
                            if (state.followedMemberId != null) {
                                Snackbar(
                                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    action = {
                                        TextButton(
                                            onClick = { viewModel.stopFollowMember() },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onPrimary // or Color.White
                                            )
                                        ) {
                                            Text("Stop")
                                        }
                                    },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text(text = "You're following ${state.members?.get(state.followedMemberId)?.name}")
                                }
                            }
                        }
                    }

                    AGDetailsViewModel.AGDetailsTab.Members -> {
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
                                members = state.members!!.map { (_, member) -> member },
                                authenticatedMemberId = state.anonymousGroup!!.memberId,
                                onTapLocate = { memberId ->
                                    viewModel.onTapLocate(memberId)
                                },
                                onTapFollow = { memberId ->
                                    viewModel.onTapFollow(memberId)
                                })
                        }
                    }
                }
            }
        }
    }
}
