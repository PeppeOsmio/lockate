package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.UserActivity

/** A member in a list: avatar, name and one line of status, with an optional [trailing] slot */
@Composable
fun MemberRow(
    name: String,
    memberId: String,
    supporting: String,
    modifier: Modifier = Modifier,
    isMe: Boolean = false,
    activity: UserActivity = UserActivity.Unknown,
    paused: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.clickableIf(onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            MemberAvatar(
                name = name,
                memberId = memberId,
                isMe = isMe,
                activity = activity,
                paused = paused,
            )
        },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
    )
}

/** A circle in a list: circle icon, name and one line (radius, who is inside) */
@Composable
fun CircleRow(
    name: String,
    supporting: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.clickableIf(onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier =
                    Modifier.size(40.dp).clip(CircleShape).background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = scheme.onTertiaryContainer,
                )
            }
        },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
    )
}

private fun Modifier.clickableIf(onClick: (() -> Unit)?): Modifier =
    if (onClick != null) clickable(onClick = onClick) else this

@ThemePreviews
@Composable
private fun ListRowsPreview() {
    PreviewSurface {
        Column {
            MemberRow(name = "Giulia", memberId = "me", supporting = "You", isMe = true)
            MemberRow(
                name = "Marco",
                memberId = "a",
                supporting = "In a vehicle · 1 min ago",
                activity = UserActivity.InVehicle,
                onClick = {},
            )
            MemberRow(name = "Sara", memberId = "b", supporting = "Paused sharing", paused = true)
            CircleRow(
                name = "Home",
                supporting = "250 m · Sara",
                onClick = {},
                trailing = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Mute Home")
                    }
                },
            )
        }
    }
}
