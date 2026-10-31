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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.composables.ErrorDialog
import com.peppeosmio.lockate.ui.composables.PeekHeaderBottomSheetScaffold
import com.peppeosmio.lockate.ui.composables.PermissionsRequester
import com.peppeosmio.lockate.ui.composables.ServerUnreachableBanner
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsViewModel
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LocationStreamState
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MapPoint
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MembersMap
import com.peppeosmio.lockate.utils.LoadingState
import com.peppeosmio.lockate.utils.LockatePermissions
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
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
fun MapScreen(
    navigateToConnectionSettings: (route: ConnectionSettingsRoute) -> Unit,
    navigateToCreateAG: (connectionSettingsId: Long) -> Unit,
    navigateToJoinAG: (connectionSettingsId: Long) -> Unit,
    navigateToInvite: (anonymousGroupInternalId: Long) -> Unit,
    navigateToManageConnections: () -> Unit,
    navigateToSettings: () -> Unit,
    startLocationService: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
    detailsViewModel: AGDetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val details by detailsViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState, snackbarHostState = snackbarHostState
    )
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    var oldLocations by remember { mutableStateOf(emptySet<String>()) }
    val oldLocationsJobs = remember { mutableMapOf<String, Job>() }
    var isMyLocationOld by remember { mutableStateOf(false) }
    var isMyLocationOldJob = remember<Job?> { null }

    val selectedGroup = state.selectedGroup
    val activeGroupInternalId = selectedGroup?.takeIf { it.isMember && it.existsRemote }?.internalId

    LaunchedEffect(state.selectedConnectionId, activeGroupInternalId) {
        state.selectedConnectionId?.let { detailsViewModel.selectGroup(it, activeGroupInternalId) }
    }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            viewModel.refreshServersReachability()
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

    // rememberMapState owns the base style, so switching theme or style setting updates it on recomposition
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(
            resolveMapStyleUrl(isSystemInDarkTheme(), state.mapStyleUrls.light, state.mapStyleUrls.dark)
        ),
        initialCameraPosition = CameraPosition(
            center = Coordinates.NAPOLI.toMapLibreComposePosition(), zoom = 15.0
        )
    )
    LaunchedEffect(true) {
        detailsViewModel.cameraPositionEvents.collect { location ->
            mapState.animateCamera(
                CameraUpdate(center = location.toMapLibreComposePosition(), zoom = 15.0)
            )
        }
    }

    val membersPoints = remember(details.members, oldLocations, details.anonymousGroup, details.selectedMemberId) {
        val group = details.anonymousGroup ?: return@remember null
        details.members?.mapNotNull { (memberId, member) ->
            if (group.memberId == memberId) {
                return@mapNotNull null
            }
            val locationRecord = member.lastLocationRecord ?: return@mapNotNull null
            MapPoint(
                coordinates = locationRecord.coordinates,
                name = member.name,
                isOld = oldLocations.contains(memberId),
                isSelected = memberId == details.selectedMemberId,
                id = memberId
            )
        }
    }

    val myPoint = remember(details.myLocationRecordFromGPS, details.anonymousGroup, isMyLocationOld) {
        val record = details.myLocationRecordFromGPS ?: return@remember null
        MapPoint(
            coordinates = record.coordinates,
            name = "You",
            isOld = isMyLocationOld,
            isSelected = false,
            id = details.anonymousGroup?.memberId ?: "me"
        )
    }

    LaunchedEffect(details.members) {
        details.members?.forEach { (memberId, member) ->
            oldLocations -= member.id
            oldLocationsJobs[member.id]?.cancel()
            if (member.lastLocationRecord == null) {
                return@forEach
            }
            val now = Clock.System.now()
            val locationTimestamp = member.lastLocationRecord.timestamp.toInstant(TimeZone.UTC)
            val msToWait = (locationTimestamp.plus(1.minutes) - now).inWholeMilliseconds.coerceAtLeast(0)
            oldLocationsJobs[member.id] = launch {
                delay(msToWait)
                if (details.members?.get(memberId)?.lastLocationRecord?.timestamp == member.lastLocationRecord.timestamp) {
                    oldLocations += memberId
                }
            }
        }
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

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { detailsViewModel.startMyLocation() })
    PermissionsRequester(
        permissionRequests = LockatePermissions.PERMISSION_REQUESTS,
        requestPermissions = { permissions -> multiplePermissionsLauncher.launch(permissions.toTypedArray()) },
        checkPermissionGranted = { permission -> viewModel.checkPermissionGranted(permission) },
        onPermissionsRequested = {
            startLocationService()
            detailsViewModel.startMyLocation()
        })

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
        copyToClipboard("coordinates", coordinates.toShareableString(), "Coordinates copied to clipboard")
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

    // Collapse before switching to the details, otherwise they'd show up fully expanded and then close
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
        AlertDialog(
            title = { Text("Delete $groupName") },
            text = { Text("Are you sure you want to delete this anonymous group? This action is permanent!") },
            dismissButton = {
                TextButton(onClick = detailsViewModel::hideDeleteAGDialog) { Text("No, don't delete") }
            },
            confirmButton = {
                TextButton(onClick = detailsViewModel::deleteAnonymousGroup) { Text("Yes, delete") }
            },
            onDismissRequest = detailsViewModel::hideDeleteAGDialog)
    }

    if (details.showLeaveAGDialog) {
        AlertDialog(
            title = { Text("Leave $groupName") },
            text = { Text("Are you sure you want to leave this anonymous group?") },
            dismissButton = {
                TextButton(onClick = detailsViewModel::hideLeaveAGDialog) { Text("No, stay") }
            },
            confirmButton = {
                TextButton(onClick = detailsViewModel::leaveAnonymousGroup) { Text("Yes, leave") }
            },
            onDismissRequest = detailsViewModel::hideLeaveAGDialog)
    }

    details.makeAdminDialogMemberId?.let { memberId ->
        val memberName = details.members?.get(memberId)?.name ?: "this member"
        AlertDialog(
            title = { Text("Make $memberName an admin?") },
            text = { Text("They will be able to delete the group and make other members admins. This can't be undone from the app.") },
            dismissButton = {
                TextButton(onClick = detailsViewModel::hideMakeAdminDialog) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = detailsViewModel::makeMemberAdmin) { Text("Make admin") }
            },
            onDismissRequest = detailsViewModel::hideMakeAdminDialog)
    }

    val connectionId = state.selectedConnectionId
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sheetKind = sheetContentKind(state, details)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ServerDrawer(
                connections = state.connections?.values?.toList() ?: emptyList(),
                selectedConnectionId = connectionId,
                unreachableConnectionIds = state.unreachableConnectionIds,
                onSelectConnection = {
                    viewModel.selectConnection(it)
                    coroutineScope.launch { drawerState.close() }
                },
                onAddServer = {
                    coroutineScope.launch { drawerState.close() }
                    navigateToConnectionSettings(
                        ConnectionSettingsRoute(initialConnectionSettingsId = null, showBackButton = true)
                    )
                },
                onManageServers = {
                    coroutineScope.launch { drawerState.close() }
                    navigateToManageConnections()
                },
                onSettings = {
                    coroutineScope.launch { drawerState.close() }
                    navigateToSettings()
                })
        }) {
        PeekHeaderBottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.55f,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            sheetHeader = {
                MembersSheetHeader(
                    kind = sheetKind,
                    mapState = state,
                    details = details,
                    onCreateGroup = { connectionId?.let(navigateToCreateAG) },
                    onJoinGroup = { connectionId?.let(navigateToJoinAG) },
                    onRemoveGroup = viewModel::removeLocalGroup,
                    onBackToMembers = detailsViewModel::clearSelectedMember,
                    onToggleFollow = detailsViewModel::setFollowSelectedMember
                )
            },
            sheetBody = if (sheetKind.hasBody) {
                {
                    MembersSheetBody(
                        kind = sheetKind,
                        details = details,
                        onSelectMember = ::selectMember,
                        onCopyCoordinates = ::copyCoordinates,
                        onOpenInMaps = ::openInMaps,
                        onCopyMemberId = { member ->
                            copyToClipboard("memberId", member.id, "Member id copied to clipboard")
                        },
                        onMakeAdmin = detailsViewModel::showMakeAdminDialog
                    )
                }
            } else null
        ) { sheetPeekHeight ->
            Box(modifier = Modifier.fillMaxSize()) {
                MembersMap(
                    modifier = Modifier.fillMaxSize(),
                    mapState = mapState,
                    membersPoints = membersPoints,
                    myPoint = myPoint,
                    myDeviceOrientation = details.myDeviceOrientation,
                    compassTopPadding = statusBarTop + 72.dp,
                    bottomPadding = sheetPeekHeight,
                    onTapMember = ::selectMember,
                    onMapGesture = detailsViewModel::onMapGesture
                )

                if (state.isGroupPickerOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(interactionSource = null, indication = null) {
                                viewModel.closeGroupPicker()
                            })
                }

                Column(modifier = Modifier.align(Alignment.TopCenter)) {
                    FloatingGroupBar(
                        selectedGroup = selectedGroup,
                        groups = filterGroups(sortGroups(state.groups ?: emptyList()), state.groupQuery),
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
                            GroupMenuItems(
                                group = selectedGroup,
                                isSharingLocation = details.anonymousGroup?.sendLocation == true,
                                canToggleSharing = details.anonymousGroup != null,
                                isAdmin = details.anonymousGroup?.memberIsAGAdmin == true,
                                onToggleSharing = { dismiss(); detailsViewModel.toggleAGShareLocation() },
                                onInvite = { dismiss(); selectedGroup?.let { navigateToInvite(it.internalId) } },
                                onLeave = { dismiss(); detailsViewModel.showLeaveAGDialog() },
                                onRemove = { dismiss(); viewModel.removeLocalGroup() },
                                onDelete = { dismiss(); detailsViewModel.showDeleteAGDialog() })
                        })
                    if (!state.isGroupPickerOpen &&
                        (state.isServerUnreachable || details.remoteDataLoadingState == LoadingState.Failed ||
                            details.locationStreamState == LocationStreamState.Reconnecting)
                    ) {
                        ServerUnreachableBanner(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            onRetry = {
                                viewModel.retryServer()
                                detailsViewModel.remoteOperations()
                            })
                    }
                }

                if (scaffoldState.bottomSheetState.targetValue != SheetValue.Expanded) {
                    FloatingActionButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .padding(bottom = sheetPeekHeight),
                        onClick = detailsViewModel::onTapMyLocation
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_location_searching_24),
                            contentDescription = "My location"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMenuItems(
    group: AnonymousGroup?,
    isSharingLocation: Boolean,
    canToggleSharing: Boolean,
    isAdmin: Boolean,
    onToggleSharing: () -> Unit,
    onInvite: () -> Unit,
    onLeave: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit
) {
    group ?: return
    val isActive = group.isMember && group.existsRemote
    if (isActive) {
        DropdownMenuItem(
            leadingIcon = { Checkbox(checked = isSharingLocation, onCheckedChange = null) },
            text = { Text("Share location") },
            enabled = canToggleSharing,
            onClick = onToggleSharing
        )
    }
    if (isActive) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Invite") },
            onClick = onInvite
        )
        DropdownMenuItem(
            leadingIcon = { Icon(painterResource(R.drawable.outline_logout_24), contentDescription = null) },
            text = { Text("Leave") },
            onClick = onLeave
        )
        if (isAdmin) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                text = { Text("Delete") },
                onClick = onDelete
            )
        }
    } else {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            text = { Text("Remove from device") },
            onClick = onRemove
        )
    }
}
