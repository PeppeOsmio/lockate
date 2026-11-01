package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.UserActivity

@Composable
internal fun ActivityIndicator(activity: UserActivity, modifier: Modifier = Modifier) {
    val (icon, description) = when (activity) {
        UserActivity.Unknown -> return
        UserActivity.Still -> R.drawable.baseline_pause_24 to "Still"
        UserActivity.OnFoot -> R.drawable.baseline_directions_walk_24 to "On foot"
        UserActivity.OnBicycle -> R.drawable.baseline_directions_bike_24 to "By bike"
        UserActivity.InVehicle -> R.drawable.baseline_directions_car_24 to "In a vehicle"
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        modifier = modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
