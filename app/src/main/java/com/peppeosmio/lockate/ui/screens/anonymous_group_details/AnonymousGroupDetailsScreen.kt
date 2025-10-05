package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.ui.composables.SmallCircularProgressIndicator
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

enum class AnonymousGroupDetailsTab {
    Map, Members, Admin
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
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

    val membersPoints = remember(state.members, oldLocations, state.anonymousGroup) {
        if (state.anonymousGroup == null) {
            null
        } else {
            state.members?.map { (memberId, member) ->
                if (state.anonymousGroup!!.memberId == memberId) {
                    null
                } else {
                    val locationRecord = member.lastLocationRecord ?: return@map null
                    MapPoint(
                        coordinates = locationRecord.coordinates,
                        name = member.name,
                        isOld = oldLocations.contains(memberId)
                    )
                }
            }?.filterNotNull()
        }
    }

    val myPoint = remember(state.anonymousGroup, state.members, state.myCoordinates, isMyLocationOld) {
        if (state.members == null || state.anonymousGroup == null || state.myCoordinates == null) {
            return@remember null
        }
        val me = state.members!![state.anonymousGroup!!.memberId]
        if (me == null) {
            Log.e("", "Can't find own user in members list! id=${state.anonymousGroup!!.memberId}")
            return@remember null
        }
        MapPoint(
            coordinates = state.myCoordinates!!, name = "You", isOld = isMyLocationOld
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
                if (state.members!![memberId]?.lastLocationRecord?.id == member.lastLocationRecord.id) {
                    oldLocations += memberId
                }
            }
        }
    }

    LaunchedEffect(state.myCoordinates) {
        state.myCoordinates?.let {
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
            anonymousGroupId = anonymousGroupId, connectionSettingsId = connectionSettingsId
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
                        connectionSettingsId = connectionSettingsId,
                        anonymousGroupId = anonymousGroupId
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
                            membersPoints = membersPoints,
                            myPoint = myPoint,
                            onTapMyLocation = {
                                viewModel.onTapMyLocation()
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
                                members = state.members!!.map { (_, member) -> member },
                                authenticatedMemberId = state.anonymousGroup!!.memberId,
                                onLocateClick = { memberId ->
                                    viewModel.moveToMember(memberId)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(AnonymousGroupDetailsTab.Map.ordinal)
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
