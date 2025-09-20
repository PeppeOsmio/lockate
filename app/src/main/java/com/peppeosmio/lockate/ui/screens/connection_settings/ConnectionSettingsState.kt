package com.peppeosmio.lockate.ui.screens.connection_settings

import com.peppeosmio.lockate.utils.ErrorDialogInfo

data class ConnectionSettingsState(
    val url: String = "",
    val apiKey: String = "",
    val showLoadingOverlay: Boolean = true,
    val errorDialogInfo: ErrorDialogInfo? = null,
    val requireApiKey: Boolean = false,
)
