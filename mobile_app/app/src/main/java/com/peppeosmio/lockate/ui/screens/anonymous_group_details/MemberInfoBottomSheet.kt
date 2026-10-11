package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.utils.DateTimeUtils
import kotlinx.datetime.format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberInfoBottomSheet(
    member: AGMember,
    isMe: Boolean,
    onDismiss: () -> Unit,
    onShowOnMap: () -> Unit,
    onCopyCoordinates: () -> Unit,
    onOpenInMaps: () -> Unit,
    onCopyId: () -> Unit
) {
    val lastSeen = member.lastLocationRecord?.timestamp
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = member.name, style = MaterialTheme.typography.titleLarge)
            if (isMe) {
                Spacer(Modifier.size(8.dp))
                MemberBadge("You")
            }
            if (member.isAGAdmin) {
                Spacer(Modifier.size(8.dp))
                MemberBadge("Admin")
            }
        }
        Spacer(Modifier.height(8.dp))

        InfoRow(
            label = "Last seen",
            value = lastSeen?.let {
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

        if (member.lastLocationRecord != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ActionRow(R.drawable.outline_navigation_24, "Show on map", onShowOnMap)
            ActionRow(R.drawable.outline_content_copy_24, "Copy coordinates", onCopyCoordinates)
            ActionRow(R.drawable.outline_map_24, "Open in maps app", onOpenInMaps)
        }
        Spacer(Modifier.height(32.dp))
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
