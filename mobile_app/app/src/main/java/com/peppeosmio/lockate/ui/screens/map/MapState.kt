package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.service.MapStyleUrls
import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class MapState(
    val connections: Map<Long, Connection>? = null,
    val selectedConnectionId: Long? = null,
    // how many groups this phone has on each server, for the drawer
    val groupCounts: Map<Long, Int> = emptyMap(),
    // how many members each group has, for the group picker
    val memberCounts: Map<Long, Int> = emptyMap(),
    val groups: List<AnonymousGroup>? = null,
    val selectedGroupInternalId: Long? = null,
    val isServerUnreachable: Boolean = false,
    val isGroupPickerOpen: Boolean = false,
    val groupQuery: String = "",
    val showLoadingOverlay: Boolean = false,
    val dialogError: SnackbarErrorMessage? = null,
    val mapStyleUrls: MapStyleUrls = MapStyleUrls(),
    // both location permissions are on, so sharing can send and the map can show this phone
    val canShare: Boolean = true,
) {
    val selectedGroup: AnonymousGroup?
        get() = groups?.firstOrNull { it.internalId == selectedGroupInternalId }

    val selectedConnection
        get() = selectedConnectionId?.let { connections?.get(it) }
}
