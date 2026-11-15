package com.peppeosmio.lockate.ui.screens.manage_connections

import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class ManageConnectionsState(
    val connections: List<Connection>? = null,
    /** What each server says about its version, by connection id, once it answered. */
    val versionLabels: Map<Long, String> = emptyMap(),
    val showLoadingOverlay: Boolean = true,
    val selectedConnectionIdForDelete: Long? = null,
    val showDeleteConfirmDialog: Boolean = false,
    val dialogError: SnackbarErrorMessage? = null,
)
