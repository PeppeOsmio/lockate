package com.peppeosmio.lockate.ui.screens.home_page

import com.peppeosmio.lockate.domain.ConnectionSettings
import com.peppeosmio.lockate.utils.ErrorDialogInfo

data class HomePageState(
    val showLoadingOverlay: Boolean = false,
    val shouldRedirectToCredentialsPage: Boolean = false,
    val pageSelected: Int = 0,
    val showLogoutDialog: Boolean = false,
    val isSearchBarOpen: Boolean = false,
    val searchText: String = "",
    val registeredOnSearch: ((query: String) -> Unit)? = null,
    val registeredOnTapFab: (() -> Unit)? = null,
    val dialogErrorDialogInfo: ErrorDialogInfo? = null,
    val selectedConnectionSettingsId: Long? = null,
    val connectionSettings: List<ConnectionSettings>? = null,
    val isConnectionsMenuOpen: Boolean = false,
)
