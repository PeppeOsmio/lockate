package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.utils.DateTimeUtils
import kotlinx.datetime.format

@Composable
fun AGMembersList(
    authenticatedMemberId: String,
    members: List<AGMember>,
    modifier: Modifier = Modifier,
    onTapMember: (memberId: String) -> Unit,
    onCopyCoordinates: (member: AGMember) -> Unit,
    onOpenInMaps: (member: AGMember) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(members, key = { member -> member.id }) { member ->
            MemberRow(
                modifier = Modifier.animateItem(),
                member = member,
                isMe = authenticatedMemberId == member.id,
                onTap = { onTapMember(member.id) },
                onCopyCoordinates = { onCopyCoordinates(member) },
                onOpenInMaps = { onOpenInMaps(member) })
        }
    }
}

@Composable
fun MemberRow(
    modifier: Modifier = Modifier,
    member: AGMember,
    isMe: Boolean,
    onTap: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onOpenInMaps: () -> Unit
) {
    val hasLocation = member.lastLocationRecord != null
    val cardColors = CardDefaults.cardColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onTap,
        enabled = isMe || hasLocation,
        // a member without a location isn't tappable but shouldn't look grayed out
        colors = cardColors.copy(
            disabledContainerColor = cardColors.containerColor,
            disabledContentColor = cardColors.contentColor
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = if (isMe) {
                            "(You) - ${member.name}"
                        } else {
                            member.name
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (member.isAGAdmin) {
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(size = 12.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(8.dp)
                        ) {
                            Text(
                                "Admin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (hasLocation) {
                    IconButton(onClick = onCopyCoordinates) {
                        Icon(
                            painter = painterResource(R.drawable.outline_content_copy_24),
                            contentDescription = "Copy coordinates",
                        )
                    }
                    IconButton(onClick = onOpenInMaps) {
                        Icon(
                            painter = painterResource(R.drawable.outline_map_24),
                            contentDescription = "Open in maps",
                        )
                    }
                }
            }
            Text(
                text = "Id: ${member.id}", style = MaterialTheme.typography.bodyMedium
            )
            LastSeenText(lastSeen = member.lastLocationRecord?.timestamp)
            Text(
                text = "Joined: ${
                    DateTimeUtils.utcToCurrentTimeZone(member.createdAt).format(
                        DateTimeUtils.DATE_FORMAT
                    )
                }", style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
