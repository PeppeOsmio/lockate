package com.peppeosmio.lockate.ui.screens.home_page

import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.utils.ErrorInfo

data class HomePageState(
    val showLoadingOverlay: Boolean = false,
    val pageSelected: Int = 0,
    val showLogoutDialog: Boolean = false,
    val isSearchBarOpen: Boolean = false,
    val searchText: String = "",
    val registeredOnSearch: ((query: String) -> Unit)? = null,
    val registeredOnTapFab: (() -> Unit)? = null,
    val dialogErrorInfo: ErrorInfo? = null,
    val selectedConnectionId: Long? = null,
    val connections: Map<Long, Connection>? = null,
    val isConnectionsDialogOpen: Boolean = false,
)
