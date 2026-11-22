package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.ui.theme.LockateTheme

/**
 * The initials of a member on their own colour (primary for this phone's member). A moving
 * [activity] adds a badge in its colour; a member who [paused] sharing is greyed out.
 */
@Composable
fun MemberAvatar(
    name: String,
    memberId: String,
    modifier: Modifier = Modifier,
    isMe: Boolean = false,
    activity: UserActivity = UserActivity.Unknown,
    paused: Boolean = false,
    size: Dp = 40.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when {
            paused -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
            isMe -> scheme.primary to scheme.onPrimary
            else -> LockateTheme.colors.member(memberId).let { it.color to it.onColor }
        }
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = memberInitials(name),
                style =
                    if (size < 40.dp) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.titleMedium,
                color = content,
            )
        }
        val badge = if (paused) null else activity.badgeIcon()
        if (badge != null) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .offset(x = size * 0.1f, y = size * 0.1f)
                        .size(size * 0.4f)
                        .border(2.dp, scheme.surface, CircleShape)
                        .clip(CircleShape)
                        .background(LockateTheme.colors.activity(activity))
                        .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(badge),
                    contentDescription = null,
                    tint = scheme.surface,
                )
            }
        }
    }
}

private fun UserActivity.badgeIcon(): Int? =
    when (this) {
        UserActivity.Unknown,
        UserActivity.Still -> null
        UserActivity.OnFoot -> R.drawable.baseline_directions_walk_24
        UserActivity.OnBicycle -> R.drawable.baseline_directions_bike_24
        UserActivity.InVehicle -> R.drawable.baseline_directions_car_24
    }

internal fun memberInitials(name: String): String =
    name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

@ThemePreviews
@Composable
private fun MemberAvatarPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MemberAvatar(name = "Giulia", memberId = "me", isMe = true)
            MemberAvatar(name = "Marco", memberId = "a", activity = UserActivity.InVehicle)
            MemberAvatar(name = "Sara Rossi", memberId = "b", activity = UserActivity.OnFoot)
            MemberAvatar(name = "Luca", memberId = "c", activity = UserActivity.OnBicycle)
            MemberAvatar(name = "Anna", memberId = "d", paused = true)
        }
    }
}
