package com.peppeosmio.lockate.ui.screens.places

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.ui.composables.FloatingTopBar
import com.peppeosmio.lockate.ui.composables.MemberRow
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LastSeenFreshness
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MemberMarker
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MyMarker
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.PlaceOverlay
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.PlaceStyle
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.dotColor
import com.peppeosmio.lockate.ui.screens.map.memberFreshness
import com.peppeosmio.lockate.ui.screens.map.memberStatus
import com.peppeosmio.lockate.ui.screens.map.occupiedPlaces
import com.peppeosmio.lockate.ui.screens.map.resolveMapStyleUrl
import com.peppeosmio.lockate.ui.screens.map.youFirst
import com.peppeosmio.lockate.ui.screens.map.zoomToFitPlace
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.toDpPadding

// the map above the details, status bar included
private val MAP_HEIGHT = 340.dp
// the details start this far over the bottom of the map, so their rounded top shows on it
private val SHEET_OVERLAP = 28.dp
// the place's diameter takes this share of the map's width
private const val PLACE_WIDTH_FRACTION = 0.38

/** A place: where it is, who is in it now, its notifications; editing and deleting start here */
@OptIn(ExperimentalTime::class)
@Composable
fun PlaceDetailScreen(
    anonymousGroupInternalId: Long,
    placeId: String,
    navigateBack: () -> Unit,
    navigateToEditor: () -> Unit,
    navigateToMember: (memberId: String) -> Unit,
    viewModel: PlacesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(anonymousGroupInternalId) { viewModel.load(anonymousGroupInternalId) }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it.text) }
    }

    val place = state.places.firstOrNull { it.id == placeId }
    // deleted here or by someone else: there is nothing left to show. Going back is dropped while
    // another screen (the editor) is on top, so this waits for the page to be on top again
    val isGone = !state.isLoading && place == null
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isOnTop = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(isGone, isOnTop) { if (isGone && isOnTop) navigateBack() }

    val now by
        produceState(Clock.System.now()) {
            while (true) {
                value = Clock.System.now()
                delay(30.seconds)
            }
        }

    Box(Modifier.fillMaxSize()) {
        if (place == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val inside =
                youFirst(
                    occupiedPlaces(state.places, state.members)[place].orEmpty(),
                    state.myMemberId,
                )
            PlaceMap(place = place, inside = inside, state = state, onTapMember = navigateToMember)
            Surface(
                modifier = Modifier.fillMaxSize().padding(top = MAP_HEIGHT - SHEET_OVERLAP),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 3.dp,
            ) {
                Details(
                    place = place,
                    inside = inside,
                    state = state,
                    now = now,
                    onMutedChange = { viewModel.setTargetMuted(place.id, it) },
                    onMember = navigateToMember,
                    onDelete = { showDeleteDialog = true },
                )
            }
            FloatingTopBar(
                title = place.name,
                navigation = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier =
                    Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                actions = {
                    IconButton(onClick = navigateToEditor) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${place.name}")
                    }
                },
            )
            if (showDeleteDialog) {
                DeleteDialog(
                    placeName = place.name,
                    groupName = state.groupName,
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deletePlace(place.id)
                    },
                    onDismiss = { showDeleteDialog = false },
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun PlaceMap(
    place: Place,
    inside: List<AGMember>,
    state: PlacesScreenState,
    onTapMember: (memberId: String) -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toDouble()
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
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
                    center = place.center.toMapLibreComposePosition(),
                    zoom =
                        zoomToFitPlace(
                            place.center.latitude,
                            place.radiusMeters,
                            screenWidthDp,
                            PLACE_WIDTH_FRACTION,
                        ),
                    // centred between the top bar and the details
                    padding =
                        PaddingValues(top = statusBarTop + 72.dp, bottom = SHEET_OVERLAP)
                            .toDpPadding(LayoutDirection.Ltr),
                ),
        )
    val now = Clock.System.now()
    val others = memberPoints(inside, state.myMemberId, now)
    val me = inside.firstOrNull { it.id == state.myMemberId }

    Box(Modifier.fillMaxWidth().height(MAP_HEIGHT).clipToBounds()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState) {
            // The data licences require showing the attribution declared by the style's sources
            ExpandingAttributionButton(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = SHEET_OVERLAP + 8.dp),
                expandedContent = { attributions, textStyle ->
                    AttributionLinks(
                        attributions,
                        textStyle = textStyle,
                        breakWithinAttribution = true,
                    )
                },
            )
        }
        PlaceOverlay(mapState, place, PlaceStyle.Outline)
        me?.lastLocationRecord?.let { record ->
            MarkerAt(mapState, record.coordinates) {
                MyMarker(center = it, orientation = null, isOld = false)
            }
        }
        others.forEach { member ->
            key(member.id) {
                MarkerAt(mapState, member.coordinates) {
                    MemberMarker(tip = it, member = member, onClick = { onTapMember(member.id) })
                }
            }
        }
    }
}

@Composable
private fun MarkerAt(
    mapState: MapState,
    coordinates: Coordinates,
    content: @Composable (DpOffset) -> Unit,
) {
    val projection by
        remember(mapState.cameraPosition, coordinates) {
            derivedStateOf {
                mapState.screenLocationFromPosition(coordinates.toMapLibreComposePosition())
            }
        }
    projection?.let { content(it) }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun Details(
    place: Place,
    inside: List<AGMember>,
    state: PlacesScreenState,
    now: Instant,
    onMutedChange: (Boolean) -> Unit,
    onMember: (memberId: String) -> Unit,
    onDelete: () -> Unit,
) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    val isMuted = place.id in state.mutedTargets
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
            Text(place.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${formatRadius(place.radiusMeters)} radius · everyone in ${state.groupName} sees it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListItem(
            modifier =
                Modifier.toggleable(
                    value = !isMuted,
                    role = Role.Switch,
                    onValueChange = { onMutedChange(!it) },
                ),
            colors = transparent,
            leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            headlineContent = { Text("Notifications") },
            supportingContent = { Text("When someone arrives or leaves. Only on this phone.") },
            trailingContent = { Switch(checked = !isMuted, onCheckedChange = null) },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Text(
            buildAnnotatedString {
                append("Here now")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(" · ${inside.size}")
                }
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (inside.isEmpty()) {
            Text(
                "No one is here now",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        for (member in inside) {
            val isMe = member.id == state.myMemberId
            val freshness = memberFreshness(member, now)
            MemberRow(
                name = member.name,
                nameSuffix = if (isMe) "You" else null,
                memberId = member.id,
                // the place is this page's own, so the status leaves it out
                supporting = memberStatus(member, emptyList(), now),
                isMe = isMe,
                activity = member.lastLocationRecord?.activity ?: UserActivity.Unknown,
                paused = freshness == LastSeenFreshness.Stale,
                statusColor = freshness.dotColor(),
                onClick = { onMember(member.id) },
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDelete,
            enabled = !state.isDeleting,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 16.dp),
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Delete place")
        }
    }
}

@Composable
private fun DeleteDialog(
    placeName: String,
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text("Delete $placeName?") },
        text = { Text("The place disappears for everyone in $groupName.") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete")
            }
        },
        onDismissRequest = onDismiss,
    )
}
