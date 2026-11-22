package com.peppeosmio.lockate.ui.screens.map

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.ui.composables.ErrorDialog
import com.peppeosmio.lockate.ui.composables.PeekHeaderBottomSheetScaffold
import com.peppeosmio.lockate.ui.composables.PermissionsRequester
import com.peppeosmio.lockate.ui.composables.ServerUnreachableBanner
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.ui.routes.ServerScreenMode
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsViewModel
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LastSeenFreshness
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LocationStreamState
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MARKER_MOVE_DURATION_MS
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MapPoint
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MembersMap
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.lastSeenFreshness
import com.peppeosmio.lockate.utils.DateTimeUtils
import com.peppeosmio.lockate.utils.LoadingState
import com.peppeosmio.lockate.utils.LockatePermissions
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun MapScreen(
    navigateToConnectionSettings: (route: ConnectionSettingsRoute) -> Unit,
    navigateToCreateAG: (connectionSettingsId: Long) -> Unit,
    navigateToJoinAG: (connectionSettingsId: Long) -> Unit,
    navigateToInvite: (anonymousGroupInternalId: Long) -> Unit,
    navigateToCircles: (anonymousGroupInternalId: Long) -> Unit,
    navigateToSettings: () -> Unit,
    startLocationService: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
    detailsViewModel: AGDetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val details by detailsViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = true)
    val scaffoldState =
        rememberBottomSheetScaffoldState(
            bottomSheetState = sheetState,
            snackbarHostState = snackbarHostState,
        )
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    // the same clock and freshness as the dot next to each member's name
    val now by
        produceState(Clock.System.now()) {
            while (true) {
                value = Clock.System.now()
                delay(30.seconds)
            }
        }
    var isMyLocationOld by remember { mutableStateOf(false) }
    var isMyLocationOldJob = remember<Job?> { null }

    val selectedGroup = state.selectedGroup
    val activeGroupInternalId = selectedGroup?.takeIf { it.isMember && it.existsRemote }?.internalId

    LaunchedEffect(state.selectedConnectionId, activeGroupInternalId) {
        state.selectedConnectionId?.let { detailsViewModel.selectGroup(it, activeGroupInternalId) }
    }

    // The check at launch may have run while the server was down: the group's channel connecting
    // means it's back, so check again rather than keep the banner until Retry is tapped
    LaunchedEffect(details.remoteDataLoadingState) {
        if (state.isServerUnreachable && details.remoteDataLoadingState == LoadingState.Success) {
            viewModel.retryServer()
        }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            viewModel.refreshServers()
        }
    }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { message ->
            if (message.errorInfo != null) {
                viewModel.showErrorDialog(message)
            } else {
                snackbarHostState.showSnackbar(message = message.text, withDismissAction = true)
            }
        }
    }
    LaunchedEffect(true) {
        detailsViewModel.snackbarEvents.collect { message ->
            if (message.errorInfo != null) {
                detailsViewModel.showErrorDialog(message)
            } else {
                snackbarHostState.showSnackbar(message = message.text, withDismissAction = true)
            }
        }
    }
    LaunchedEffect(true) {
        viewModel.navigateToConnectionSettingsEvents.collect { navigateToConnectionSettings(it) }
    }

    // rememberMapState owns the base style, so switching theme or style setting updates it on
    // recomposition
    val mapState =
        rememberMapState(
            baseStyle =
                BaseStyle.Uri(
                    resolveMapStyleUrl(
                        isSystemInDarkTheme(),
                        state.mapStyleUrls.light,
                        state.mapStyleUrls.dark,
                    )
                ),
            initialCameraPosition =
                CameraPosition(
                    center = Coordinates.NAPOLI.toMapLibreComposePosition(),
                    zoom = 15.0,
                ),
        )
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toDouble()
    LaunchedEffect(true) {
        detailsViewModel.cameraPositionEvents.collect { target ->
            val zoom =
                target.fitRadiusMeters?.let {
                    zoomToFitCircle(
                        latitude = target.coordinates.latitude,
                        radiusMeters = it,
                        viewportWidthDp = screenWidthDp,
                    )
                } ?: 15.0
            // a map gesture cancels the animation by throwing, which would otherwise end this
            // collector
            try {
                mapState.animateCamera(
                    CameraUpdate(
                        center = target.coordinates.toMapLibreComposePosition(),
                        zoom = zoom,
                    ),
                    CameraAnimation.Ease { duration = MARKER_MOVE_DURATION_MS.milliseconds },
                )
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    val membersPoints =
        remember(details.members, now, details.anonymousGroup, details.selectedMemberId) {
            val group = details.anonymousGroup ?: return@remember null
            details.members?.mapNotNull { (memberId, member) ->
                if (group.memberId == memberId) {
                    return@mapNotNull null
                }
                val locationRecord = member.lastLocationRecord ?: return@mapNotNull null
                val isLive =
                    lastSeenFreshness(locationRecord.timestamp, now) == LastSeenFreshness.Live
                MapPoint(
                    coordinates = locationRecord.coordinates,
                    name = member.name,
                    isOld = !isLive,
                    isSelected = memberId == details.selectedMemberId,
                    id = memberId,
                    activity = locationRecord.activity,
                    caption =
                        if (isLive) null
                        else
                            DateTimeUtils.formatRelative(
                                locationRecord.timestamp.toInstant(TimeZone.UTC),
                                now,
                            ),
                )
            }
        }

    val myPoint =
        remember(details.myLocationRecordFromGPS, details.anonymousGroup, isMyLocationOld) {
            val record = details.myLocationRecordFromGPS ?: return@remember null
            MapPoint(
                coordinates = record.coordinates,
                name = "You",
                isOld = isMyLocationOld,
                isSelected = false,
                id = details.anonymousGroup?.memberId ?: "me",
            )
        }

    LaunchedEffect(details.myLocationRecordFromGPS) {
        details.myLocationRecordFromGPS?.let {
            isMyLocationOldJob?.cancel()
            isMyLocationOld = false
            isMyLocationOldJob = launch {
                delay(60000L)
                isMyLocationOld = true
            }
        }
    }

    LifecycleStartEffect(detailsViewModel) {
        detailsViewModel.resumeMyLocation()
        detailsViewModel.startLocationStream()
        onStopOrDispose {
            detailsViewModel.pauseMyLocation()
            detailsViewModel.stopLocationStream()
        }
    }

    val multiplePermissionsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { detailsViewModel.startMyLocation() },
        )
    PermissionsRequester(
        permissionRequests = LockatePermissions.PERMISSION_REQUESTS,
        requestPermissions = { permissions ->
            multiplePermissionsLauncher.launch(permissions.toTypedArray())
        },
        checkPermissionGranted = { permission -> viewModel.checkPermissionGranted(permission) },
        onPermissionsRequested = {
            startLocationService()
            detailsViewModel.startMyLocation()
        },
    )

    BackHandler(enabled = state.isGroupPickerOpen || details.selectedMemberId != null) {
        if (state.isGroupPickerOpen) {
            viewModel.closeGroupPicker()
        } else {
            detailsViewModel.clearSelectedMember()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    fun copyToClipboard(label: String, text: String, confirmation: String) {
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
            snackbarHostState.showSnackbar(confirmation)
        }
    }

    fun copyCoordinates(member: AGMember) {
        val coordinates = member.lastLocationRecord?.coordinates ?: return
        copyToClipboard(
            "coordinates",
            coordinates.toShareableString(),
            "Coordinates copied to clipboard",
        )
    }

    // The geo label is a fixed string, not the member name, so the decrypted name
    // is never handed to a third-party app
    fun openInMaps(member: AGMember) {
        val geoUri = member.lastLocationRecord?.coordinates?.toGeoUri() ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri.toUri()))
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch { snackbarHostState.showSnackbar("No maps app installed") }
        }
    }

    // Collapse before switching to the details, otherwise they'd show up fully expanded and then
    // close
    fun selectMember(memberId: String) {
        coroutineScope.launch {
            sheetState.partialExpand()
            detailsViewModel.selectMember(memberId)
        }
    }

    if (state.showLoadingOverlay || details.showLoadingOverlay) {
        Dialog(onDismissRequest = {}) { CircularProgressIndicator() }
    }
    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }
    details.dialogError?.let { ErrorDialog(it, onDismiss = detailsViewModel::hideErrorDialog) }

    val groupName = details.anonymousGroup?.name ?: selectedGroup?.name ?: ""
    if (details.showDeleteAGDialog) {
        DeleteGroupDialog(
            groupName = groupName,
            membersCount = details.members?.size ?: 1,
            onConfirm = detailsViewModel::deleteAnonymousGroup,
            onDismiss = detailsViewModel::hideDeleteAGDialog,
        )
    }
    if (details.showLeaveAGDialog) {
        LeaveGroupDialog(
            groupName = groupName,
            onConfirm = detailsViewModel::leaveAnonymousGroup,
            onDismiss = detailsViewModel::hideLeaveAGDialog,
        )
    }
    details.makeAdminDialogMemberId?.let { memberId ->
        MakeAdminDialog(
            memberName = details.members?.get(memberId)?.name ?: "this member",
            onConfirm = detailsViewModel::makeMemberAdmin,
            onDismiss = detailsViewModel::hideMakeAdminDialog,
        )
    }

    val connectionId = state.selectedConnectionId
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val content = sheetContent(state, details)
    val isOffline =
        state.isServerUnreachable ||
            details.remoteDataLoadingState == LoadingState.Failed ||
            details.locationStreamState == LocationStreamState.Reconnecting
    val sheetActions =
        MapSheetActions(
            onSharingChange = { detailsViewModel.toggleAGShareLocation() },
            onCreateGroup = { connectionId?.let(navigateToCreateAG) },
            onJoinGroup = { connectionId?.let(navigateToJoinAG) },
            onRemoveGroup = viewModel::removeLocalGroup,
            onInvite = { selectedGroup?.let { navigateToInvite(it.internalId) } },
            onOpenCircles = { selectedGroup?.let { navigateToCircles(it.internalId) } },
            onShowCircle = { circle ->
                coroutineScope.launch {
                    sheetState.partialExpand()
                    detailsViewModel.showCircle(circle)
                }
            },
            onSelectMember = ::selectMember,
            onBackToGroup = detailsViewModel::clearSelectedMember,
            onFollow = {
                coroutineScope.launch {
                    sheetState.partialExpand()
                    detailsViewModel.setFollowSelectedMember(true)
                }
            },
            onOpenInMaps = ::openInMaps,
            onCopyCoordinates = ::copyCoordinates,
            onCopyMemberId = { member ->
                copyToClipboard("memberId", member.id, "Member ID copied to clipboard")
            },
            onMakeAdmin = detailsViewModel::showMakeAdminDialog,
            onMuteMember = detailsViewModel::setMemberMuted,
        )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ServerDrawer(
                connections = state.connections?.values?.sortedBy { it.name.lowercase() }.orEmpty(),
                selectedConnectionId = connectionId,
                groupCounts = state.groupCounts,
                onSelectConnection = {
                    viewModel.selectConnection(it)
                    coroutineScope.launch { drawerState.close() }
                },
                onEditServer = { id ->
                    coroutineScope.launch { drawerState.close() }
                    navigateToConnectionSettings(
                        ConnectionSettingsRoute(ServerScreenMode.Edit, connectionId = id)
                    )
                },
                onAddServer = {
                    coroutineScope.launch { drawerState.close() }
                    navigateToConnectionSettings(ConnectionSettingsRoute(ServerScreenMode.Add))
                },
                onSettings = {
                    coroutineScope.launch { drawerState.close() }
                    navigateToSettings()
                },
            )
        },
    ) {
        PeekHeaderBottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            sheetHeader = {
                MapSheetHeader(
                    content = content,
                    selectedGroup = selectedGroup,
                    details = details,
                    now = now,
                    sharingSupporting = if (isOffline) "Sends again once connected" else null,
                    actions = sheetActions,
                )
            },
            sheetBody =
                if (content.hasBody) {
                    {
                        MapSheetBody(
                            content = content,
                            details = details,
                            now = now,
                            actions = sheetActions,
                        )
                    }
                } else null,
        ) { sheetPeekHeight ->
            Box(modifier = Modifier.fillMaxSize()) {
                MembersMap(
                    modifier = Modifier.fillMaxSize(),
                    mapState = mapState,
                    membersPoints = membersPoints,
                    circles = details.circles,
                    selectedCircleId = details.selectedCircleId,
                    onTapCircle = detailsViewModel::toggleCircle,
                    myPoint = myPoint,
                    myDeviceOrientation = details.myDeviceOrientation,
                    compassTopPadding = statusBarTop + 72.dp,
                    bottomPadding = sheetPeekHeight,
                    onTapMember = ::selectMember,
                    onMapGesture = detailsViewModel::onMapGesture,
                )

                if (state.isGroupPickerOpen) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.32f))
                                .clickable(interactionSource = null, indication = null) {
                                    viewModel.closeGroupPicker()
                                }
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FloatingGroupBar(
                        selectedGroup = selectedGroup,
                        subtitle =
                            details.members
                                ?.takeIf { details.anonymousGroup != null }
                                ?.let { if (it.size == 1) "Only you" else "${it.size} members" },
                        groups =
                            filterGroups(sortGroups(state.groups ?: emptyList()), state.groupQuery),
                        memberCounts = state.memberCounts,
                        isPickerOpen = state.isGroupPickerOpen,
                        query = state.groupQuery,
                        onQueryChange = viewModel::setGroupQuery,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onOpenPicker = viewModel::openGroupPicker,
                        onClosePicker = viewModel::closeGroupPicker,
                        onSelectGroup = viewModel::selectGroup,
                        onCreateGroup = {
                            viewModel.closeGroupPicker()
                            connectionId?.let(navigateToCreateAG)
                        },
                        onJoinGroup = {
                            viewModel.closeGroupPicker()
                            connectionId?.let(navigateToJoinAG)
                        },
                        groupMenu = { dismiss ->
                            selectedGroup?.let { group ->
                                GroupMenuItems(
                                    group = group,
                                    isAdmin = details.anonymousGroup?.memberIsAGAdmin == true,
                                    onInvite = {
                                        dismiss()
                                        navigateToInvite(group.internalId)
                                    },
                                    onCircles = {
                                        dismiss()
                                        navigateToCircles(group.internalId)
                                    },
                                    onLeave = {
                                        dismiss()
                                        detailsViewModel.showLeaveAGDialog()
                                    },
                                    onDelete = {
                                        dismiss()
                                        detailsViewModel.showDeleteAGDialog()
                                    },
                                    onRemove = {
                                        dismiss()
                                        viewModel.removeLocalGroup()
                                    },
                                )
                            }
                        },
                    )
                    if (!state.isGroupPickerOpen && content is SheetContent.Following) {
                        details.members?.get(content.memberId)?.let { member ->
                            FollowingPill(
                                name = member.name,
                                onStop = { detailsViewModel.setFollowSelectedMember(false) },
                            )
                        }
                    } else if (!state.isGroupPickerOpen && isOffline) {
                        ServerUnreachableBanner(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            onRetry = {
                                viewModel.retryServer()
                                detailsViewModel.remoteOperations()
                            },
                        )
                    }
                }

                // Without a body the sheet settles as expanded at its collapsed height, so only an
                // expanded body hides the button
                if (
                    !content.hasBody ||
                        scaffoldState.bottomSheetState.targetValue != SheetValue.Expanded
                ) {
                    FloatingActionButton(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .padding(bottom = sheetPeekHeight),
                        onClick = detailsViewModel::onTapMyLocation,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_location_searching_24),
                            contentDescription = "Center on me",
                        )
                    }
                }
            }
        }
    }
}

/** Under the top bar while the camera follows a member; ✕ stops following */
@Composable
private fun FollowingPill(name: String, onStop: () -> Unit) {
    Button(onClick = onStop, contentPadding = PaddingValues(start = 16.dp, end = 12.dp)) {
        Icon(
            painterResource(R.drawable.outline_navigation_24),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("Following $name")
        Spacer(Modifier.size(8.dp))
        Icon(Icons.Default.Close, contentDescription = "Stop following", Modifier.size(18.dp))
    }
}
