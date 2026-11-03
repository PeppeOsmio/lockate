package com.peppeosmio.lockate.ui.screens.circles

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import com.peppeosmio.lockate.ui.composables.PeekHeaderBottomSheetScaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.CircleOverlay
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.CircleStyle
import com.peppeosmio.lockate.ui.screens.map.circleWidthFraction
import com.peppeosmio.lockate.ui.screens.map.resolveMapStyleUrl
import com.peppeosmio.lockate.ui.screens.map.zoomToFitCircle
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
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// the circle is zoomed into view when it takes less or more than this share of the map's width
private const val MIN_VISIBLE_WIDTH_FRACTION = 0.15
private const val MAX_VISIBLE_WIDTH_FRACTION = 0.85

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = true),
        snackbarHostState = snackbarHostState
    )
    // a new circle starts with the fields in view, since there is nothing to see on the map yet
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
                else zoomToFitCircle(center.latitude, radiusMeters, screenWidthDp)
            ), CameraAnimation.Ease { duration = 400.milliseconds }
        )
    }

    val hasCenter = state.center != null
    LaunchedEffect(hasCenter) { if (hasCenter) showCircle(recenter = false) }

    fun showCircleAfterFrame(recenter: Boolean) {
        coroutineScope.launch {
            withFrameNanos { }
            showCircle(recenter)
        }
    }

    PeekHeaderBottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetHeader = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (state.isEditing) "Edit circle" else "Add circle",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                Button(onClick = viewModel::save, enabled = state.canSave) {
                    Text(if (state.isSaving) "Saving…" else "OK")
                }
            }
        },
        sheetBody = {
            EditorFields(
                state = state,
                viewModel = viewModel,
                onCoordinatesApplied = { showCircleAfterFrame(recenter = true) },
                onRadiusChangeFinished = { showCircleAfterFrame(recenter = false) })
        }) {
        Box(Modifier.fillMaxSize()) {
            EditorMap(state = state, mapState = mapState, viewModel = viewModel)
            BackButton(navigateBack)
        }
    }
}

@Composable
private fun EditorFields(
    state: CircleEditorState,
    viewModel: CircleEditorViewModel,
    onCoordinatesApplied: () -> Unit,
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
        var coordinatesHadFocus by remember { mutableStateOf(false) }
        val areCoordinatesValid = parseCoordinates(state.coordinatesText) != null
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    // the map goes to the point once the field is left, like after the radius
                    if (coordinatesHadFocus && !it.isFocused && areCoordinatesValid) {
                        onCoordinatesApplied()
                    }
                    coordinatesHadFocus = it.isFocused
                },
            value = state.coordinatesText,
            onValueChange = viewModel::setCoordinatesText,
            label = { Text("Coordinates") },
            placeholder = { Text("41.902800, 12.496400") },
            isError = state.coordinatesText.isNotEmpty() && !areCoordinatesValid,
            supportingText = {
                Text(
                    if (state.center == null && state.coordinatesText.isEmpty())
                        "Tap the map or write latitude, longitude"
                    else "Latitude, longitude"
                )
            },
            trailingIcon = {
                IconButton(
                    enabled = areCoordinatesValid && !state.isSaving,
                    onClick = {
                        if (coordinatesHadFocus) focusManager.clearFocus()
                        else onCoordinatesApplied()
                    }) {
                    Icon(Icons.Default.Place, contentDescription = "Go to the coordinates")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text, imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus() }),
            singleLine = true,
            enabled = !state.isSaving
        )
        if (state.center != null) {
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
}

@Composable
private fun EditorMap(
    state: CircleEditorState, mapState: MapState, viewModel: CircleEditorViewModel
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
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 64.dp, top = 8.dp),
            expandedContent = { attributions, textStyle ->
                AttributionLinks(attributions, textStyle = textStyle, breakWithinAttribution = true)
            }
        )
    }

    state.otherCircles.forEach { CircleOverlay(mapState, it, CircleStyle.Faint) }
    state.center?.let { center ->
        CircleOverlay(
            mapState,
            Circle(id = "editing", name = state.name, center = center, radiusMeters = state.radiusMeters),
            CircleStyle.Editing
        )
    }
}
