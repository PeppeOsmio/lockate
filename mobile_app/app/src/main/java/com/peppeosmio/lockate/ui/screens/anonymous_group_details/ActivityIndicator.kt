package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.UserActivity

internal val UserActivity.label: String
    get() = when (this) {
        UserActivity.Unknown -> "Unknown"
        UserActivity.Still -> "Still"
        UserActivity.OnFoot -> "On foot"
        UserActivity.OnBicycle -> "By bike"
        UserActivity.InVehicle -> "In a vehicle"
    }

/** Shows nothing when the user is still or the activity isn't known */
@Composable
internal fun ActivityIndicator(
    activity: UserActivity, modifier: Modifier = Modifier, size: Dp = 18.dp
) {
    val icon = when (activity) {
        UserActivity.Unknown, UserActivity.Still -> return
        UserActivity.OnFoot -> R.drawable.baseline_directions_walk_24
        UserActivity.OnBicycle -> R.drawable.baseline_directions_bike_24
        UserActivity.InVehicle -> R.drawable.baseline_directions_car_24
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = activity.label,
        modifier = modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
