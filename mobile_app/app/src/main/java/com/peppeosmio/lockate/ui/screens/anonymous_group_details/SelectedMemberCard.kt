package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember

@Composable
fun SelectedMemberCard(
    modifier: Modifier = Modifier,
    member: AGMember,
    isFollowing: Boolean,
    onToggleFollow: (Boolean) -> Unit,
    onCopyCoordinates: () -> Unit,
    onOpenInMaps: () -> Unit,
    onClose: () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LastSeenText(lastSeen = member.lastLocationRecord?.timestamp)
            }
            if (member.lastLocationRecord != null) {
                FilledTonalIconToggleButton(checked = isFollowing, onCheckedChange = onToggleFollow) {
                    Icon(
                        painter = painterResource(R.drawable.outline_navigation_24),
                        contentDescription = if (isFollowing) "Stop following" else "Follow member"
                    )
                }
                IconButton(onClick = onCopyCoordinates) {
                    Icon(
                        painter = painterResource(R.drawable.outline_content_copy_24),
                        contentDescription = "Copy coordinates"
                    )
                }
                IconButton(onClick = onOpenInMaps) {
                    Icon(
                        painter = painterResource(R.drawable.outline_map_24),
                        contentDescription = "Open in maps"
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}
