package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.ui.composables.MemberAvatar
import com.peppeosmio.lockate.ui.composables.NameLine
import com.peppeosmio.lockate.ui.composables.StatusLine
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.LastSeenFreshness
import com.peppeosmio.lockate.ui.screens.anonymous_group_details.dotColor
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Avatar, name and status, the row both the member details and Following start with */
@Composable
private fun MemberTitle(
    member: AGMember,
    isMe: Boolean,
    status: String,
    freshness: LastSeenFreshness,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the group")
        }
        MemberAvatar(
            name = member.name,
            memberId = member.id,
            isMe = isMe,
            activity = member.lastLocationRecord?.activity ?: UserActivity.Unknown,
            paused = freshness == LastSeenFreshness.Stale,
            size = 48.dp,
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            NameLine(
                name = member.name,
                suffix = if (isMe) "You" else null,
                style = MaterialTheme.typography.titleLarge,
            )
            StatusLine(
                text = status,
                dotColor = freshness.dotColor(),
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2,
            )
        }
    }
}

/** Collapsed member details: who, and the actions that need the map (follow, open elsewhere) */
@Composable
fun MemberHeader(
    member: AGMember,
    isMe: Boolean,
    status: String,
    freshness: LastSeenFreshness,
    actions: MapSheetActions,
) {
    MemberTitle(member, isMe, status, freshness, onBack = actions.onBackToGroup)
    if (member.lastLocationRecord == null) return
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isMe) {
            FilledTonalButton(onClick = actions.onFollow) {
                Icon(
                    painterResource(R.drawable.outline_navigation_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Follow")
            }
        }
        FilledTonalButton(onClick = { actions.onOpenInMaps(member) }) {
            Icon(
                painterResource(R.drawable.outline_map_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Maps")
        }
    }
}

/** While following, the sheet stays collapsed on who is followed */
@Composable
fun FollowingHeader(
    member: AGMember,
    status: String,
    freshness: LastSeenFreshness,
    onBack: () -> Unit,
) {
    MemberTitle(member, isMe = false, status, freshness, onBack = onBack)
}

@OptIn(ExperimentalTime::class)
@Composable
fun MemberBody(
    member: AGMember,
    isMe: Boolean,
    isMuted: Boolean,
    canMakeAdmin: Boolean,
    now: Instant,
    actions: MapSheetActions,
    modifier: Modifier = Modifier,
) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(modifier = modifier) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        if (!isMe) {
            ListItem(
                modifier =
                    Modifier.toggleable(
                        value = !isMuted,
                        role = Role.Switch,
                        onValueChange = { actions.onMuteMember(member.id, !it) },
                    ),
                colors = transparent,
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                headlineContent = { Text("Notifications about ${member.name}") },
                supportingContent = { Text("Places and sharing") },
                trailingContent = { Switch(checked = !isMuted, onCheckedChange = null) },
            )
        }
        member.lastLocationRecord?.let { record ->
            ListItem(
                colors = transparent,
                leadingContent = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                headlineContent = { Text("Last update") },
                supportingContent = {
                    Text(friendlyDateTime(record.timestamp, now))
                },
                trailingContent = {
                    IconButton(onClick = { actions.onCopyCoordinates(member) }) {
                        Icon(
                            painterResource(R.drawable.outline_content_copy_24),
                            contentDescription = "Copy coordinates",
                        )
                    }
                },
            )
        }
        ListItem(
            colors = transparent,
            leadingContent = {
                Icon(painterResource(R.drawable.baseline_security_24), contentDescription = null)
            },
            headlineContent = { Text(if (member.isAGAdmin) "Admin" else "Member") },
            supportingContent = {
                Text("Joined " + friendlyDay(member.createdAt, now))
            },
            trailingContent =
                if (canMakeAdmin) {
                    {
                        TextButton(onClick = { actions.onMakeAdmin(member.id) }) {
                            Text("Make admin")
                        }
                    }
                } else null,
        )
        ListItem(
            colors = transparent,
            leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            headlineContent = { Text("Member ID") },
            supportingContent = {
                Text(member.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                IconButton(onClick = { actions.onCopyMemberId(member) }) {
                    Icon(
                        painterResource(R.drawable.outline_content_copy_24),
                        contentDescription = "Copy member ID",
                    )
                }
            },
        )
    }
}
