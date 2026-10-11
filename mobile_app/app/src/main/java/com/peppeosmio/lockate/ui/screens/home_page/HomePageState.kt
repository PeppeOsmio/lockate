package com.peppeosmio.lockate.ui.screens.home_page

import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import com.peppeosmio.lockate.domain.Connection

data class HomePageState(
    val isSearchBarOpen: Boolean = false,
    val searchText: String = "",
    val registeredOnSearch: ((query: String) -> Unit)? = null,
    val registeredOnTapFab: (() -> Unit)? = null,
    val dialogError: SnackbarErrorMessage? = null,
    val selectedConnectionId: Long? = null,
    val connections: Map<Long, Connection>? = null,
    val isConnectionsDialogOpen: Boolean = false,
)
