package com.peppeosmio.lockate.ui.screens.manage_connections

import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import com.peppeosmio.lockate.domain.Connection

data class ManageConnectionsState(
    val connections: List<Connection>? = null,
    val showLoadingOverlay: Boolean = true,
    val selectedConnectionIdForDelete: Long? = null,
    val showDeleteConfirmDialog: Boolean = false,
    val dialogError: SnackbarErrorMessage? = null
)
