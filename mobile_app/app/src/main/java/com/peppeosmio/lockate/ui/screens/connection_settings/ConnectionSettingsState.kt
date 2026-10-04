package com.peppeosmio.lockate.ui.screens.connection_settings

import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class ConnectionSettingsState(
    val name: String = "",
    val url: String = "",
    val apiKey: String = "",
    val showLoadingOverlay: Boolean = true,
    val dialogError: SnackbarErrorMessage? = null,
    val requireApiKey: Boolean = false,
)
