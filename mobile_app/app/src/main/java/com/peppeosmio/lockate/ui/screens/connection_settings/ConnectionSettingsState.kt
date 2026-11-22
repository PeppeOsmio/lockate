package com.peppeosmio.lockate.ui.screens.connection_settings

import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.routes.ServerScreenMode
import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class ConnectionSettingsState(
    val mode: ServerScreenMode = ServerScreenMode.FirstLaunch,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    // the name shown as the title while editing, before any change to the field
    val savedName: String = "",
    val name: String = "",
    // until the user writes a name, it follows the host of the address
    val isNameEdited: Boolean = false,
    val url: String = "",
    val apiKey: String = "",
    val groups: List<AnonymousGroup> = emptyList(),
    val versionLabel: String? = null,
    val showRemoveDialog: Boolean = false,
    val dialogError: SnackbarErrorMessage? = null,
)
