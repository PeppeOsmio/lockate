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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.AGMember

@Composable
fun AGMembersList(
    authenticatedMemberId: String,
    members: List<AGMember>,
    modifier: Modifier = Modifier,
    onTapMember: (memberId: String) -> Unit,
    onMoreOptions: (memberId: String) -> Unit
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
                onMoreOptions = { onMoreOptions(member.id) })
        }
    }
}

@Composable
fun MemberRow(
    modifier: Modifier = Modifier,
    member: AGMember,
    isMe: Boolean,
    onTap: () -> Unit,
    onMoreOptions: () -> Unit
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
                        text = member.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isMe) {
                        Spacer(Modifier.size(8.dp))
                        MemberBadge("You")
                    }
                    if (member.isAGAdmin) {
                        Spacer(Modifier.size(8.dp))
                        MemberBadge("Admin")
                    }
                }
                IconButton(onClick = onMoreOptions) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Member options")
                }
            }
            LastSeenText(lastSeen = member.lastLocationRecord?.timestamp)
        }
    }
}

@Composable
internal fun MemberBadge(text: String) {
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
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
