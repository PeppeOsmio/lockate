package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.utils.DateTimeUtils
import kotlinx.datetime.format

/**
 * Name, admin flag, last location and follow toggle: the part of the member details that stays
 * visible when the sheet is collapsed.
 */
@Composable
fun MemberDetailsHeader(
    member: AGMember,
    isMe: Boolean,
    isFollowing: Boolean,
    onBack: () -> Unit,
    onToggleFollow: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to members")
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = member.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isMe) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.isAGAdmin) {
                    AdminIndicator()
                    Spacer(Modifier.size(12.dp))
                }
                LastSeenText(lastSeen = member.lastLocationRecord?.timestamp)
            }
        }
        if (member.lastLocationRecord != null && !isMe) {
            FilledTonalIconToggleButton(checked = isFollowing, onCheckedChange = onToggleFollow) {
                Icon(
                    painter = painterResource(R.drawable.outline_navigation_24),
                    contentDescription = if (isFollowing) "Stop following" else "Follow member"
                )
            }
        }
    }
}

@Composable
fun MemberDetailsBody(
    modifier: Modifier = Modifier,
    member: AGMember,
    canMakeAdmin: Boolean,
    onCopyCoordinates: () -> Unit,
    onOpenInMaps: () -> Unit,
    onCopyId: () -> Unit,
    onMakeAdmin: () -> Unit
) {
    Column(modifier = modifier) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        InfoRow(
            label = "Last location",
            value = member.lastLocationRecord?.timestamp?.let {
                DateTimeUtils.utcToCurrentTimeZone(it).format(DateTimeUtils.DATE_FORMAT)
            } ?: "never")
        InfoRow(
            label = "Joined",
            value = DateTimeUtils.utcToCurrentTimeZone(member.createdAt)
                .format(DateTimeUtils.DATE_FORMAT))
        ListItem(
            overlineContent = { Text("Member id") },
            headlineContent = { Text(member.id) },
            trailingContent = {
                IconButton(onClick = onCopyId) {
                    Icon(
                        painter = painterResource(R.drawable.outline_content_copy_24),
                        contentDescription = "Copy member id"
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (member.lastLocationRecord != null || canMakeAdmin) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        if (member.lastLocationRecord != null) {
            ActionRow(R.drawable.outline_content_copy_24, "Copy coordinates", onCopyCoordinates)
            ActionRow(R.drawable.outline_map_24, "Open in maps app", onOpenInMaps)
        }
        if (canMakeAdmin) {
            ActionRow(R.drawable.baseline_security_24, "Make admin", onMakeAdmin)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ActionRow(iconRes: Int, text: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) },
        headlineContent = { Text(text) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
