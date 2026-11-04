package com.peppeosmio.lockate.ui.screens.circles

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.peppeosmio.lockate.service.Place
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import com.peppeosmio.lockate.ui.composables.PeekHeaderBottomSheetScaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.CircleOverlay
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.CircleStyle
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.MyMarker
import com.peppeosmio.lockate.ui.screens.map.circleWidthFraction
import com.peppeosmio.lockate.ui.screens.map.resolveMapStyleUrl
import com.peppeosmio.lockate.ui.screens.map.zoomToFitCircle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.toDpPadding
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// the circle is zoomed into view when it takes less or more than this share of the map's width
private const val MIN_VISIBLE_WIDTH_FRACTION = 0.15
private const val MAX_VISIBLE_WIDTH_FRACTION = 0.85
private const val MY_LOCATION_ZOOM = 15.0
// the sheet is still once its top stays put this many frames in a row
private const val SHEET_SETTLED_FRAMES = 3
private const val MAX_SHEET_SETTLE_FRAMES = 60

@Composable
fun CircleEditorScreen(
    anonymousGroupInternalId: Long,
    circleId: String?,
    navigateBack: () -> Unit,
    viewModel: CircleEditorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(anonymousGroupInternalId, circleId) {
        viewModel.load(anonymousGroupInternalId, circleId)
    }
    LaunchedEffect(Unit) { viewModel.navigateBackEvents.collect { navigateBack() } }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            BackButton(navigateBack)
        }
    } else {
        EditorContent(state = state, viewModel = viewModel, navigateBack = navigateBack)
    }
}

@Composable
private fun BoxScope.BackButton(navigateBack: () -> Unit) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(8.dp),
        shape = CircleShape,
        shadowElevation = 4.dp
    ) {
        IconButton(onClick = navigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    state: CircleEditorState, viewModel: CircleEditorViewModel, navigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toDouble()
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = true),
        snackbarHostState = snackbarHostState
    )
    // a new circle starts with the hint in view, since there is nothing to see on the map yet
    LaunchedEffect(Unit) { if (!state.isEditing) scaffoldState.bottomSheetState.expand() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it.text) }
    }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(
            resolveMapStyleUrl(
                isSystemInDarkTheme(), state.mapStyleUrls.light, state.mapStyleUrls.dark
            )
        ),
        initialCameraPosition = CameraPosition(
            center = state.initialCamera.toMapLibreComposePosition(), zoom = 15.0
        )
    )

    // read when called, not when the lambda was made: the end of a tap on the slider comes before
    // the recomposition that has the new radius
    val latestState by rememberUpdatedState(state)

    val density = LocalDensity.current
    var mapHeightPx by remember { mutableIntStateOf(0) }
    var searchBarBottomPx by remember { mutableIntStateOf(0) }

    // the search bar covers the top of the map and the sheet the bottom, so the circle is centred
    // in what is left between them
    fun uncoveredMap(): PaddingValues {
        val top = with(density) { searchBarBottomPx.toDp() }
        val sheetTop = runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrNull()
            ?: return PaddingValues(top = top)
        return PaddingValues(
            top = top, bottom = with(density) { (mapHeightPx - sheetTop).coerceAtLeast(0f).toDp() }
        )
    }

    // recenter also moves the camera when the circle is already in view
    suspend fun showCircle(recenter: Boolean) {
        val center = latestState.center ?: return
        val radiusMeters = latestState.radiusMeters
        val fraction = circleWidthFraction(
            latitude = center.latitude,
            radiusMeters = radiusMeters,
            zoom = mapState.cameraPosition.zoom,
            viewportWidthDp = screenWidthDp
        )
        val fits = fraction in MIN_VISIBLE_WIDTH_FRACTION..MAX_VISIBLE_WIDTH_FRACTION
        if (fits && !recenter) return
        mapState.animateCamera(
            CameraUpdate(
                center = center.toMapLibreComposePosition(),
                zoom = if (fits) mapState.cameraPosition.zoom
                else zoomToFitCircle(center.latitude, radiusMeters, screenWidthDp),
                padding = uncoveredMap().toDpPadding(LayoutDirection.Ltr)
            ), CameraAnimation.Ease { duration = 400.milliseconds }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.cameraEvents.collect { coordinates ->
            // a map gesture cancels the animation by throwing, which would otherwise end this collector
            try {
                mapState.animateCamera(
                    CameraUpdate(
                        center = coordinates.toMapLibreComposePosition(),
                        zoom = MY_LOCATION_ZOOM,
                        padding = uncoveredMap().toDpPadding(LayoutDirection.Ltr)
                    ), CameraAnimation.Ease { duration = 400.milliseconds }
                )
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    // The fields appear once the circle has a point and the sheet grows to fit them, so the circle
    // is centred once the sheet stops moving
    fun showCircleWhenSheetSettles(recenter: Boolean) {
        coroutineScope.launch {
            var lastSheetTop: Float? = null
            var settledFrames = 0
            var frames = 0
            while (settledFrames < SHEET_SETTLED_FRAMES && frames < MAX_SHEET_SETTLE_FRAMES) {
                withFrameNanos { }
                frames++
                val sheetTop = runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrNull()
                settledFrames = if (sheetTop == lastSheetTop) settledFrames + 1 else 0
                lastSheetTop = sheetTop
            }
            showCircle(recenter)
        }
    }

    val hasCenter = state.center != null
    LaunchedEffect(hasCenter) { if (hasCenter) showCircleWhenSheetSettles(recenter = false) }

    fun copyCoordinates(coordinates: Coordinates) {
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("coordinates", coordinates.toShareableString())))
            snackbarHostState.showSnackbar("Coordinates copied to clipboard")
        }
    }

    PeekHeaderBottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetHeader = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.isEditing) "Edit circle" else "Add circle",
                        style = MaterialTheme.typography.titleLarge
                    )
                    state.center?.let { center ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Copy the coordinates") { copyCoordinates(center) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                center.toShareableString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                painter = painterResource(R.drawable.outline_content_copy_24),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Button(onClick = viewModel::save, enabled = state.canSave) {
                    Text(if (state.isSaving) "Saving…" else "OK")
                }
            }
        },
        sheetBody = {
            EditorFields(
                state = state,
                viewModel = viewModel,
                onRadiusChangeFinished = { showCircleWhenSheetSettles(recenter = false) })
        }) { sheetPeekHeight ->
        Box(Modifier
            .fillMaxSize()
            .onSizeChanged { mapHeightPx = it.height }) {
            EditorMap(state = state, mapState = mapState, viewModel = viewModel, bottomPadding = sheetPeekHeight)
            SearchBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { searchBarBottomPx = it.boundsInParent().bottom.toInt() },
                state = state,
                onQueryChange = { query ->
                    val near = mapState.cameraPosition.center
                    viewModel.setSearchQuery(query, Coordinates(latitude = near.latitude, longitude = near.longitude))
                },
                onClear = viewModel::clearSearch,
                onSelectPlace = { place ->
                    viewModel.selectPlace(place)
                    showCircleWhenSheetSettles(recenter = true)
                },
                onSelectCoordinates = {
                    viewModel.selectSearchCoordinates()
                    showCircleWhenSheetSettles(recenter = true)
                },
                navigateBack = navigateBack
            )
            if (scaffoldState.bottomSheetState.targetValue != SheetValue.Expanded) {
                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(bottom = sheetPeekHeight),
                    onClick = viewModel::goToMyLocation
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

/** Floating over the top of the map: the back button, the search and, below them, its results. */
@Composable
private fun SearchBar(
    modifier: Modifier,
    state: CircleEditorState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelectPlace: (Place) -> Unit,
    onSelectCoordinates: () -> Unit,
    navigateBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val searchCoordinates = state.searchCoordinates
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = navigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                }
                TextField(
                    modifier = Modifier.weight(1f),
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text("Search a place or coordinates", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        if (searchCoordinates != null) onSelectCoordinates()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (state.isSearching) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear the search")
                        }
                    }
                }
            }
            if (searchCoordinates != null || state.searchResults.isNotEmpty() || state.searchFailed) {
                HorizontalDivider()
                LazyColumn(modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f)) {
                    searchCoordinates?.let { coordinates ->
                        item {
                            SearchResult(
                                headline = coordinates.toShareableString(),
                                supporting = "Coordinates",
                                onClick = {
                                    focusManager.clearFocus()
                                    onSelectCoordinates()
                                })
                        }
                    }
                    items(state.searchResults) { place ->
                        SearchResult(
                            headline = place.name,
                            supporting = place.description.takeIf { it.isNotEmpty() },
                            onClick = {
                                focusManager.clearFocus()
                                onSelectPlace(place)
                            })
                    }
                    if (state.searchFailed) {
                        item {
                            Text(
                                "Can't search right now",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResult(headline: String, supporting: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun EditorFields(
    state: CircleEditorState,
    viewModel: CircleEditorViewModel,
    onRadiusChangeFinished: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.center == null) {
            Text(
                "Tap the map, or search a place or coordinates",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text("Name") },
            singleLine = true,
            enabled = !state.isSaving
        )
        var hadFocus by remember { mutableStateOf(false) }
        val isRadiusValid = parseRadiusMeters(state.radiusText) != null
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    // the circle is shown whole once the field is left, like after the slider
                    if (hadFocus && !it.isFocused) onRadiusChangeFinished()
                    hadFocus = it.isFocused
                },
            value = state.radiusText,
            onValueChange = viewModel::setRadiusText,
            label = { Text("Radius") },
            suffix = { Text("m") },
            isError = !isRadiusValid,
            supportingText = {
                if (!isRadiusValid) {
                    Text(
                        "Between ${CircleEditorViewModel.MIN_RADIUS_METERS.roundToInt()} and " +
                            "${CircleEditorViewModel.MAX_RADIUS_METERS.roundToInt()} m"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            singleLine = true,
            enabled = !state.isSaving
        )
        Slider(
            value = state.radiusMeters.toFloat(),
            onValueChange = { viewModel.setRadius(it.roundToInt().toDouble()) },
            onValueChangeFinished = onRadiusChangeFinished,
            valueRange = CircleEditorViewModel.MIN_RADIUS_METERS.toFloat()..CircleEditorViewModel.MAX_RADIUS_METERS.toFloat(),
            enabled = !state.isSaving
        )
    }
}

@Composable
private fun EditorMap(
    state: CircleEditorState, mapState: MapState, viewModel: CircleEditorViewModel, bottomPadding: Dp
) {
    val currentSetCenter by rememberUpdatedState(viewModel::setCenter)
    val interactions = remember {
        MapInteractions {
            callbacks {
                click {
                    onEvent { event ->
                        event.position?.let {
                            currentSetCenter(Coordinates(latitude = it.latitude, longitude = it.longitude))
                        }
                        ClickResult.Consume
                    }
                }
            }
        }
    }

    MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState, interactions = interactions) {
        // The data licences require showing the attribution declared by the style's sources
        ExpandingAttributionButton(
            modifier = Modifier
                .align(Alignment.BottomStart)
                // leaves room for the my-location button, so the text wraps instead of going under it
                .padding(start = 8.dp, end = 88.dp, bottom = bottomPadding + 8.dp),
            expandedContent = { attributions, textStyle ->
                AttributionLinks(attributions, textStyle = textStyle, breakWithinAttribution = true)
            }
        )
    }

    state.otherCircles.forEach { CircleOverlay(mapState, it, CircleStyle.Faint) }
    state.myLocation?.let { myLocation ->
        val projection by remember(mapState.cameraPosition, myLocation) {
            derivedStateOf { mapState.screenLocationFromPosition(myLocation.toMapLibreComposePosition()) }
        }
        projection?.let { MyMarker(center = it, orientation = null, isOld = false) }
    }
    state.center?.let { center ->
        CircleOverlay(
            mapState,
            Circle(id = "editing", name = state.name, center = center, radiusMeters = state.radiusMeters),
            CircleStyle.Editing
        )
    }
}
