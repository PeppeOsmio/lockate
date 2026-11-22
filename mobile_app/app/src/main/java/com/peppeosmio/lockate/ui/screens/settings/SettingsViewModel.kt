package com.peppeosmio.lockate.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.PlaceSearchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val mapStyleService: MapStyleService,
    private val placeSearchService: PlaceSearchService,
    private val permissionsService: PermissionsService,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mapStyleService.mapStyleUrls, placeSearchService.geocoderUrl) { styles, geocoder
                    ->
                    mapOf(
                        SettingsUrl.LightStyle to styles.light,
                        SettingsUrl.DarkStyle to styles.dark,
                        SettingsUrl.PlaceSearch to geocoder,
                    )
                }
                .collect { urls -> _state.update { it.copy(urls = urls) } }
        }
    }

    /** The permissions can change in the system settings while the screen is in the background */
    fun refreshPermissions() {
        _state.update { it.copy(permissions = permissionsService.granted()) }
    }

    fun onEdit(url: SettingsUrl) {
        _state.update { it.copy(draft = UrlDraft(url, it.urls[url] ?: "")) }
    }

    fun onDraftChanged(text: String) {
        _state.update { it.copy(draft = it.draft?.copy(text = text, isError = false)) }
    }

    fun onDismissDraft() {
        _state.update { it.copy(draft = null) }
    }

    /** Checks the URL answers like the service it replaces before saving it; empty means default */
    fun onSaveDraft() {
        val draft = state.value.draft ?: return
        viewModelScope.launch {
            _state.update { it.copy(draft = draft.copy(isChecking = true)) }
            val text = draft.text.trim()
            val valid =
                text.isEmpty() ||
                    when (draft.url) {
                        SettingsUrl.PlaceSearch -> placeSearchService.isValidGeocoderUrl(text)
                        else -> mapStyleService.isValidStyleUrl(text)
                    }
            if (!valid) {
                _state.update { it.copy(draft = draft.copy(isChecking = false, isError = true)) }
                return@launch
            }
            val urls = state.value.urls
            when (draft.url) {
                SettingsUrl.LightStyle -> mapStyleService.save(text, urls[SettingsUrl.DarkStyle])
                SettingsUrl.DarkStyle -> mapStyleService.save(urls[SettingsUrl.LightStyle], text)
                SettingsUrl.PlaceSearch -> placeSearchService.save(text)
            }
            _state.update { it.copy(draft = null) }
        }
    }

    fun onResetClicked() {
        viewModelScope.launch {
            mapStyleService.save(null, null)
            placeSearchService.save(null)
        }
    }
}
