package com.peppeosmio.lockate.ui.screens.map

import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.service.MapStyleUrls
import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class MapState(
    val connections: Map<Long, Connection>? = null,
    val selectedConnectionId: Long? = null,
    val unreachableConnectionIds: Set<Long> = emptySet(),
    val groups: List<AnonymousGroup>? = null,
    val selectedGroupInternalId: Long? = null,
    val isServerUnreachable: Boolean = false,
    val isGroupPickerOpen: Boolean = false,
    val groupQuery: String = "",
    val showLoadingOverlay: Boolean = false,
    val dialogError: SnackbarErrorMessage? = null,
    val mapStyleUrls: MapStyleUrls = MapStyleUrls(),
) {
    val selectedGroup: AnonymousGroup?
        get() = groups?.firstOrNull { it.internalId == selectedGroupInternalId }

    val selectedConnection get() = selectedConnectionId?.let { connections?.get(it) }
}
