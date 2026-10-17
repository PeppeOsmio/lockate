package com.peppeosmio.lockate.di

import com.peppeosmio.lockate.platform_service.DeviceOrientationService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsViewModel
import com.peppeosmio.lockate.ui.screens.connection_settings.ConnectionSettingsViewModel
import com.peppeosmio.lockate.ui.screens.create_anonymous_group.CreateAnonymousGroupViewModel
import com.peppeosmio.lockate.ui.screens.map.MapViewModel
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.JoinAnonymousGroupViewModel
import com.peppeosmio.lockate.ui.screens.loading.LoadingViewModel
import com.peppeosmio.lockate.ui.screens.manage_connections.ManageConnectionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<LoadingViewModel> {
        LoadingViewModel(connectionService = get<ConnectionService>())
    }

    viewModel<ConnectionSettingsViewModel> {
        ConnectionSettingsViewModel(connectionService = get<ConnectionService>())
    }

    viewModel<MapViewModel> {
        MapViewModel(
            connectionService = get<ConnectionService>(),
            anonymousGroupService = get<AnonymousGroupService>(),
            permissionsService = get<PermissionsService>()
        )
    }

    viewModel<CreateAnonymousGroupViewModel> {
        CreateAnonymousGroupViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<JoinAnonymousGroupViewModel> {
        JoinAnonymousGroupViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<AGDetailsViewModel> {
        AGDetailsViewModel(
            anonymousGroupService = get<AnonymousGroupService>(),
            locationService = get<LocationService>(),
            deviceOrientationService = get<DeviceOrientationService>()
        )
    }

    viewModel<ManageConnectionsViewModel> {
        ManageConnectionsViewModel(
            connectionService = get<ConnectionService>(),
            anonymousGroupService = get<AnonymousGroupService>()
        )
    }
}