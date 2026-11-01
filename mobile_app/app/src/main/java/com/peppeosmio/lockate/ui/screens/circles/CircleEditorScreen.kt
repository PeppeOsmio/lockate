package com.peppeosmio.lockate.ui.screens.circles

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(anonymousGroupInternalId, circleId) {
        viewModel.load(anonymousGroupInternalId, circleId)
    }
    LaunchedEffect(Unit) { viewModel.navigateBackEvents.collect { navigateBack() } }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it.text) }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            EditorMap(state = state, viewModel = viewModel)
        }

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
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 56.dp)
        )
    }
}

@Composable
private fun BoxScope.EditorMapPanel(
    state: CircleEditorState, viewModel: CircleEditorViewModel, onRadiusChangeFinished: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (state.isEditing) "Edit circle" else "Add circle",
                style = MaterialTheme.typography.titleLarge
            )
            if (state.center == null) {
                Text("Tap the map to place the circle", style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !state.isSaving
                )
                Text("Radius: ${state.radiusMeters.roundToInt()} m")
                Slider(
                    value = state.radiusMeters.toFloat(),
                    onValueChange = { viewModel.setRadius(it.roundToInt().toDouble()) },
                    onValueChangeFinished = onRadiusChangeFinished,
                    valueRange = CircleEditorViewModel.MIN_RADIUS_METERS.toFloat()..CircleEditorViewModel.MAX_RADIUS_METERS.toFloat(),
                    enabled = !state.isSaving
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::save,
                enabled = state.canSave
            ) { Text(if (state.isSaving) "Saving…" else "OK") }
        }
    }
}

@Composable
private fun BoxScope.EditorMap(state: CircleEditorState, viewModel: CircleEditorViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toDouble()
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

    // read when called, not when the lambda was made: the end of a tap on the slider comes before
    // the recomposition that has the new radius
    val latestState by rememberUpdatedState(state)

    suspend fun showWholeCircleIfNeeded() {
        val center = latestState.center ?: return
        val radiusMeters = latestState.radiusMeters
        val fraction = circleWidthFraction(
            latitude = center.latitude,
            radiusMeters = radiusMeters,
            zoom = mapState.cameraPosition.zoom,
            viewportWidthDp = screenWidthDp
        )
        if (fraction in MIN_VISIBLE_WIDTH_FRACTION..MAX_VISIBLE_WIDTH_FRACTION) return
        mapState.animateCamera(
            CameraUpdate(
                center = center.toMapLibreComposePosition(),
                zoom = zoomToFitCircle(center.latitude, radiusMeters, screenWidthDp)
            ), CameraAnimation.Ease { duration = 400.milliseconds }
        )
    }

    val hasCenter = state.center != null
    LaunchedEffect(hasCenter) { if (hasCenter) showWholeCircleIfNeeded() }

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

    EditorMapPanel(
        state = state,
        viewModel = viewModel,
        onRadiusChangeFinished = {
            coroutineScope.launch {
                withFrameNanos { }
                showWholeCircleIfNeeded()
            }
        })
}
