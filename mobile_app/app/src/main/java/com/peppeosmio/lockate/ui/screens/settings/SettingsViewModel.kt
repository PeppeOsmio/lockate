package com.peppeosmio.lockate.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.PlaceSearchService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val mapStyleService: MapStyleService,
    private val placeSearchService: PlaceSearchService
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    private val _navigateBackEvents = Channel<Unit>(Channel.BUFFERED)
    val navigateBackEvents = _navigateBackEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val urls = mapStyleService.mapStyleUrls.first()
            val geocoderUrl = placeSearchService.geocoderUrl.first()
            _state.update {
                it.copy(
                    lightStyleUrl = urls.light ?: "",
                    darkStyleUrl = urls.dark ?: "",
                    geocoderUrl = geocoderUrl ?: ""
                )
            }
        }
    }

    fun onLightStyleUrlChanged(url: String) {
        _state.update { it.copy(lightStyleUrl = url, lightStyleError = false) }
    }

    fun onDarkStyleUrlChanged(url: String) {
        _state.update { it.copy(darkStyleUrl = url, darkStyleError = false) }
    }

    fun onGeocoderUrlChanged(url: String) {
        _state.update { it.copy(geocoderUrl = url, geocoderError = false) }
    }

    fun onSaveClicked() {
        val current = state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val lightValid = current.lightStyleUrl.isBlank() || mapStyleService.isValidStyleUrl(current.lightStyleUrl)
            val darkValid = current.darkStyleUrl.isBlank() || mapStyleService.isValidStyleUrl(current.darkStyleUrl)
            val geocoderValid =
                current.geocoderUrl.isBlank() || placeSearchService.isValidGeocoderUrl(current.geocoderUrl)
            if (lightValid && darkValid && geocoderValid) {
                mapStyleService.save(current.lightStyleUrl, current.darkStyleUrl)
                placeSearchService.save(current.geocoderUrl)
                _state.update { it.copy(isSaving = false) }
                _navigateBackEvents.send(Unit)
            } else {
                _state.update {
                    it.copy(
                        isSaving = false,
                        lightStyleError = !lightValid,
                        darkStyleError = !darkValid,
                        geocoderError = !geocoderValid
                    )
                }
            }
        }
    }

    fun onResetClicked() {
        viewModelScope.launch {
            mapStyleService.save(null, null)
            placeSearchService.save(null)
            _navigateBackEvents.send(Unit)
        }
    }
}
