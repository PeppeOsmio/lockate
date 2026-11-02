package com.peppeosmio.lockate.ui.screens.circles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.MapStyleUrls
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

data class CircleEditorState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val name: String = "",
    val radiusMeters: Double = CircleEditorViewModel.DEFAULT_RADIUS_METERS,
    // what is written in the radius field, which may be half typed or out of range
    val radiusText: String = CircleEditorViewModel.DEFAULT_RADIUS_METERS.roundToInt().toString(),
    // null until the point is put on the map
    val center: Coordinates? = null,
    val initialCamera: Coordinates = Coordinates.NAPOLI,
    val otherCircles: List<Circle> = emptyList(),
    val mapStyleUrls: MapStyleUrls = MapStyleUrls(),
    val isSaving: Boolean = false,
) {
    val canSave
        get() = !isLoading && name.isNotBlank() && center != null && !isSaving &&
            parseRadiusMeters(radiusText) != null
}

/** The radius written in the field, or null when it is empty or outside the allowed range. */
internal fun parseRadiusMeters(text: String): Double? =
    text.toIntOrNull()?.toDouble()?.takeIf {
        it in CircleEditorViewModel.MIN_RADIUS_METERS..CircleEditorViewModel.MAX_RADIUS_METERS
    }

class CircleEditorViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val locationService: LocationService,
    private val mapStyleService: MapStyleService
) : ViewModel() {
    private val _state = MutableStateFlow(CircleEditorState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>()
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>()
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()

    private var group: AnonymousGroup? = null
    private var circleId: String? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            mapStyleService.mapStyleUrls.collect { urls ->
                _state.update { it.copy(mapStyleUrls = urls) }
            }
        }
    }

    /** Loads the circle to edit, or starts a new one with no point yet. */
    fun load(anonymousGroupInternalId: Long, circleId: String?) {
        if (loaded) return
        loaded = true
        this.circleId = circleId
        viewModelScope.launch {
            try {
                group = anonymousGroupService.getAGByInternalId(anonymousGroupInternalId)
                val circles = anonymousGroupService.listCircles(anonymousGroupInternalId)
                val circle = circles.firstOrNull { it.id == circleId }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isEditing = circle != null,
                        name = circle?.name ?: "",
                        radiusMeters = circle?.radiusMeters ?: DEFAULT_RADIUS_METERS,
                        radiusText = (circle?.radiusMeters ?: DEFAULT_RADIUS_METERS).roundToInt()
                            .toString(),
                        center = circle?.center,
                        initialCamera = circle?.center ?: myLocation() ?: Coordinates.NAPOLI,
                        otherCircles = circles.filter { c -> c.id != circle?.id }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage("Can't load the circle", ErrorInfo.fromException(e))
                )
            }
        }
    }

    // the map only needs to start near the phone, so a slow or missing fix isn't worth waiting for
    private suspend fun myLocation(): Coordinates? = try {
        withTimeoutOrNull(3.seconds) { locationService.getCurrentLocation() }
            ?: locationService.getLastLocation()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        locationService.getLastLocation()
    }

    fun setName(name: String) {
        _state.update { it.copy(name = name.take(PayloadCodec.MAX_CIRCLE_NAME_CHARS)) }
    }

    fun setRadius(radiusMeters: Double) {
        _state.update {
            it.copy(radiusMeters = radiusMeters, radiusText = radiusMeters.roundToInt().toString())
        }
    }

    /** The circle follows the text only while it is a valid radius, so it doesn't jump when half typed. */
    fun setRadiusText(text: String) {
        val digits = text.filter { it.isDigit() }.take(MAX_RADIUS_DIGITS)
        _state.update { it.copy(radiusText = digits, radiusMeters = parseRadiusMeters(digits) ?: it.radiusMeters) }
    }

    fun setCenter(center: Coordinates) {
        if (state.value.isSaving) return
        _state.update { it.copy(center = center) }
    }

    fun save() {
        val anonymousGroup = group ?: return
        val current = state.value
        val center = current.center ?: return
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                anonymousGroupService.upsertCircle(
                    connectionSettingsId = anonymousGroup.connectionId,
                    anonymousGroup = anonymousGroup,
                    circle = Circle(
                        id = circleId ?: UUID.randomUUID().toString(),
                        name = current.name.trim(),
                        center = center,
                        radiusMeters = current.radiusMeters
                    )
                )
                _navigateBackEvents.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false) }
                _snackbarEvents.trySend(
                    SnackbarErrorMessage("Can't save the circle", ErrorInfo.fromException(e))
                )
            }
        }
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 150.0
        const val MIN_RADIUS_METERS = 30.0
        const val MAX_RADIUS_METERS = 2000.0
        private const val MAX_RADIUS_DIGITS = 4
    }
}
