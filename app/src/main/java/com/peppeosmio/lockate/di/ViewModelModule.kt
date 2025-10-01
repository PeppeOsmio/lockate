package com.peppeosmio.lockate.di

import com.peppeosmio.lockate.service.anonymous_group.AnonymousGroupService
import com.peppeosmio.lockate.service.ConnectionSettingsService
import com.peppeosmio.lockate.service.PermissionsService
import com.peppeosmio.lockate.platform_service.LocationService
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.AnonymousGroupDetailsViewModel
import com.peppeosmio.lockate.ui.screens.anonymous_groups.AnonymousGroupsViewModel
import com.peppeosmio.lockate.ui.screens.connection_settings.ConnectionSettingsViewModel
import com.peppeosmio.lockate.ui.screens.create_anonymous_group.CreateAnonymousGroupViewModel
import com.peppeosmio.lockate.ui.screens.home_page.HomePageViewModel
import com.peppeosmio.lockate.ui.screens.join_anonymous_group.JoinAnonymousGroupViewModel
import com.peppeosmio.lockate.ui.screens.loading.LoadingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<LoadingViewModel> {
        LoadingViewModel(connectionSettingsService = get<ConnectionSettingsService>())
    }

    viewModel<ConnectionSettingsViewModel> {
        ConnectionSettingsViewModel(connectionSettingsService = get<ConnectionSettingsService>())
    }

    viewModel<HomePageViewModel> {
        HomePageViewModel(
            connectionSettingsService = get<ConnectionSettingsService>(),
            anonymousGroupService = get<AnonymousGroupService>(),
            permissionsService = get<PermissionsService>()
        )
    }

    viewModel<AnonymousGroupsViewModel> {
        AnonymousGroupsViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<CreateAnonymousGroupViewModel> {
        CreateAnonymousGroupViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<JoinAnonymousGroupViewModel> {
        JoinAnonymousGroupViewModel(anonymousGroupService = get<AnonymousGroupService>())
    }

    viewModel<AnonymousGroupDetailsViewModel> {
        AnonymousGroupDetailsViewModel(
            anonymousGroupService = get<AnonymousGroupService>(),
            locationService = get<LocationService>()
        )
    }
}