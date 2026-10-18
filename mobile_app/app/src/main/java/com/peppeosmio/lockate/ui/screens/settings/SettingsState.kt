package com.peppeosmio.lockate.ui.screens.settings

data class SettingsState(
    val lightStyleUrl: String = "",
    val darkStyleUrl: String = "",
    val lightStyleError: Boolean = false,
    val darkStyleError: Boolean = false,
    val isSaving: Boolean = false,
)
