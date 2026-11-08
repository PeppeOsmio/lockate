package com.peppeosmio.lockate.ui.screens.circles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.MapStyleUrls
import com.peppeosmio.lockate.service.Place
import com.peppeosmio.lockate.service.PlaceSearchService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.anonymous_group.PayloadCodec
import com.peppeosmio.lockate.utils.ErrorInfo
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
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
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    // what is written in the search when it reads as a point, which is never sent to the geocoder
    val searchCoordinates: Coordinates? = null,
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    // shown as a still dot: the editor takes one fix and doesn't keep the GPS on
    val myLocation: Coordinates? = null,
) {
    val canSave
        get() = !isLoading && name.isNotBlank() && center != null && !isSaving &&
            parseRadiusMeters(radiusText) != null
}

/** The "lat, lng" written in the search, or null when it isn't a pair of numbers on the globe. */
internal fun parseCoordinates(text: String): Coordinates? {
    val parts = text.trim().split(Regex("[\\s,;]+"))
    if (parts.size != 2) return null
    val latitude = parts[0].toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
    val longitude = parts[1].toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
    return Coordinates(latitude = latitude, longitude = longitude)
}

/** The radius written in the field, or null when it is empty or outside the allowed range. */
internal fun parseRadiusMeters(text: String): Double? =
    text.toIntOrNull()?.toDouble()?.takeIf {
        it in CircleEditorViewModel.MIN_RADIUS_METERS..CircleEditorViewModel.MAX_RADIUS_METERS
    }

class CircleEditorViewModel(
    private val anonymousGroupService: AnonymousGroupService,
    private val locationService: LocationService,
    private val mapStyleService: MapStyleService,
    private val placeSearchService: PlaceSearchService
) : ViewModel() {
    private val _state = MutableStateFlow(CircleEditorState())
    val state = _state.asStateFlow()
    private val _snackbarEvents = Channel<SnackbarErrorMessage>(Channel.BUFFERED)
    val snackbarEvents = _snackbarEvents.receiveAsFlow()
    private val _navigateBackEvents = Channel<Unit>(Channel.BUFFERED)
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()
    // conflated so that a tap made before the map starts collecting isn't lost
    private val _cameraEvents = Channel<Coordinates>(Channel.CONFLATED)
    val cameraEvents = _cameraEvents.receiveAsFlow()
    private var searchJob: Job? = null

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
        _state.update { it.copy(name = name.take(PayloadCodec.MAX_NAME_CHARS)) }
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

    /**
     * Searches once the text stops changing, biased to [near] so the closest matches come first.
     * Coordinates are recognised here and offered as the only result.
     */
    fun setSearchQuery(query: String, near: Coordinates) {
        searchJob?.cancel()
        val trimmed = query.trim()
        val coordinates = parseCoordinates(trimmed)
        _state.update { it.copy(searchQuery = query, searchFailed = false, searchCoordinates = coordinates) }
        if (coordinates != null || trimmed.length < MIN_SEARCH_CHARS) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            _state.update { it.copy(isSearching = true) }
            try {
                val places = placeSearchService.search(trimmed, near)
                _state.update { it.copy(searchResults = places, isSearching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(searchResults = emptyList(), isSearching = false, searchFailed = true) }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchCoordinates = null,
                isSearching = false,
                searchFailed = false
            )
        }
    }

    /** Puts the circle on [place], naming it after the place unless it already has a name. */
    fun selectPlace(place: Place) {
        if (state.value.isSaving) return
        clearSearch()
        _state.update {
            it.copy(
                center = place.coordinates,
                name = it.name.ifBlank { place.name.take(PayloadCodec.MAX_NAME_CHARS) }
            )
        }
    }

    fun selectSearchCoordinates() {
        val coordinates = state.value.searchCoordinates ?: return
        if (state.value.isSaving) return
        clearSearch()
        _state.update { it.copy(center = coordinates) }
    }

    fun goToMyLocation() {
        viewModelScope.launch {
            val coordinates = try {
                locationService.getCurrentLocation() ?: locationService.getLastLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (_: LocationDisabledException) {
                _snackbarEvents.trySend(SnackbarErrorMessage("Geolocation is disabled", errorInfo = null))
                return@launch
            } catch (_: NoPermissionException) {
                _snackbarEvents.trySend(SnackbarErrorMessage("No location permissions", errorInfo = null))
                return@launch
            } catch (_: Exception) {
                null
            }
            if (coordinates == null) {
                _snackbarEvents.trySend(SnackbarErrorMessage("Can't find your location", errorInfo = null))
                return@launch
            }
            _state.update { it.copy(myLocation = coordinates) }
            _cameraEvents.trySend(coordinates)
        }
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
        private const val MIN_SEARCH_CHARS = 3
        private val SEARCH_DEBOUNCE = 400.milliseconds
    }
}
