package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.UserActivity

internal val UserActivity.label: String
    get() =
        when (this) {
            UserActivity.Unknown -> "Unknown"
            UserActivity.Still -> "Still"
            UserActivity.OnFoot -> "On foot"
            UserActivity.OnBicycle -> "By bike"
            UserActivity.InVehicle -> "In a vehicle"
        }
