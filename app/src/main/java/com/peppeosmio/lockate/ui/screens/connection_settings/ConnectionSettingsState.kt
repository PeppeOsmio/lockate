package com.peppeosmio.lockate.ui.screens.connection_settings

import com.peppeosmio.lockate.utils.ErrorInfo

data class ConnectionSettingsState(
    val url: String = "",
    val apiKey: String = "",
    val showLoadingOverlay: Boolean = true,
    val errorInfo: ErrorInfo? = null,
    val requireApiKey: Boolean = false,
)
