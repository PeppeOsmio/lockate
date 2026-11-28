package com.peppeosmio.lockate.di

import com.peppeosmio.lockate.platform_service.ConnectivityService
import com.peppeosmio.lockate.platform_service.DeviceOrientationService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.service.ConnectionService
import com.peppeosmio.lockate.service.MapStyleService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.service.PlaceSearchService
import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.invite.InviteService
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AGDetailsViewModel
import com.peppeosmio.lockate.ui.screens.connection_settings.ConnectionSettingsViewModel
import com.peppeosmio.lockate.ui.screens.create_anonymous_group.CreateAnonymousGroupViewModel
import com.peppeosmio.lockate.ui.screens.invite.InviteViewModel
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.JoinAnonymousGroupViewModel
import com.peppeosmio.lockate.ui.screens.loading.LoadingViewModel
import com.peppeosmio.lockate.ui.screens.map.MapViewModel
import com.peppeosmio.lockate.ui.screens.permissions.PermissionsViewModel
import com.peppeosmio.lockate.ui.screens.places.PlaceEditorViewModel
import com.peppeosmio.lockate.ui.screens.places.PlacesViewModel
import com.peppeosmio.lockate.ui.screens.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<LoadingViewModel> {
        LoadingViewModel(connectionService = get<ConnectionService>())
    }

    viewModel<ConnectionSettingsViewModel> {
        ConnectionSettingsViewModel(
            connectionService = get<ConnectionService>(),
            anonymousGroupService = get<AnonymousGroupService>(),
        )
    }

    viewModel<MapViewModel> {
        MapViewModel(
            connectionService = get<ConnectionService>(),
            anonymousGroupService = get<AnonymousGroupService>(),
            permissionsService = get<PermissionsService>(),
            mapStyleService = get<MapStyleService>(),
        )
    }

    viewModel<CreateAnonymousGroupViewModel> {
        CreateAnonymousGroupViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<JoinAnonymousGroupViewModel> {
        JoinAnonymousGroupViewModel(
            inviteService = get<InviteService>(),
            connectionService = get<ConnectionService>(),
        )
    }

    viewModel<InviteViewModel> {
        InviteViewModel(
            anonymousGroupService = get<AnonymousGroupService>(),
            inviteService = get<InviteService>(),
        )
    }

    viewModel<PlacesViewModel> {
        PlacesViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<PlaceEditorViewModel> {
        PlaceEditorViewModel(
            anonymousGroupService = get<AnonymousGroupService>(),
            locationService = get<LocationService>(),
            mapStyleService = get<MapStyleService>(),
            placeSearchService = get<PlaceSearchService>(),
        )
    }

    viewModel<AGDetailsViewModel> {
        AGDetailsViewModel(
            anonymousGroupService = get<AnonymousGroupService>(),
            locationService = get<LocationService>(),
            connectivityService = get<ConnectivityService>(),
            deviceOrientationService = get<DeviceOrientationService>(),
        )
    }

    viewModel<SettingsViewModel> {
        SettingsViewModel(
            mapStyleService = get<MapStyleService>(),
            placeSearchService = get<PlaceSearchService>(),
            permissionsService = get<PermissionsService>(),
        )
    }

    viewModel<PermissionsViewModel> {
        PermissionsViewModel(
            permissionsService = get<PermissionsService>(),
            anonymousGroupService = get<AnonymousGroupService>(),
        )
    }
}
