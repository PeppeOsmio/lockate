package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.domain.anonymous_group.nearestContaining

@Composable
fun AGMembersList(
    authenticatedMemberId: String,
    members: List<AGMember>,
    circles: List<Circle>,
    modifier: Modifier = Modifier,
    onTapMember: (memberId: String) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(members, key = { member -> member.id }) { member ->
            Column(modifier = Modifier.animateItem()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                MemberRow(
                    member = member,
                    circles = circles,
                    isMe = authenticatedMemberId == member.id,
                    onTap = { onTapMember(member.id) },
                )
            }
        }
    }
}

@Composable
fun MemberRow(
    modifier: Modifier = Modifier,
    member: AGMember,
    circles: List<Circle>,
    isMe: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(member = member, isMe = isMe)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isMe) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LastSeenText(lastSeen = member.lastLocationRecord?.timestamp)
                member.lastLocationRecord?.let {
                    ActivityIndicator(it.activity, Modifier.padding(start = 6.dp))
                }
            }
        }
        member.lastLocationRecord
            ?.let { circles.nearestContaining(it.coordinates) }
            ?.let {
                PlaceIndicator(it.name)
            }
    }
}

@Composable
private fun PlaceIndicator(name: String) {
    val color = MaterialTheme.colorScheme.tertiary
    Row(modifier = Modifier.widthIn(max = 120.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemberAvatar(member: AGMember, isMe: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val palette =
        listOf(
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.surfaceVariant to scheme.onSurfaceVariant,
        )
    val (container, content) =
        if (isMe) {
            scheme.primaryContainer to scheme.onPrimaryContainer
        } else {
            palette[Math.floorMod(member.id.hashCode(), palette.size)]
        }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = memberInitials(member.name),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

internal fun memberInitials(name: String): String =
    name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
