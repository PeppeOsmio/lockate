package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.composables.MemberAvatar

/**
 * Pill floating on top of the map. Tapping the group name expands it downwards into the group
 * picker (search field, create/join, scrollable list of groups).
 */
@Composable
fun FloatingGroupBar(
    modifier: Modifier = Modifier,
    selectedGroup: AnonymousGroup?,
    isRemoved: Boolean,
    groups: List<AnonymousGroup>,
    memberCounts: Map<Long, Int>,
    isPickerOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectGroup: (anonymousGroupInternalId: Long) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    groupMenu: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    Surface(
        modifier =
            modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            if (isPickerOpen) {
                PickerHeader(query = query, onQueryChange = onQueryChange, onClose = onClosePicker)
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = onCreateGroup, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("New group")
                    }
                    FilledTonalButton(onClick = onJoinGroup, modifier = Modifier.weight(1f)) {
                        Icon(
                            painterResource(R.drawable.baseline_person_add_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Join")
                    }
                }
                LazyColumn(
                    modifier =
                        Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.6f)
                ) {
                    items(groups, key = { it.internalId }) { group ->
                        GroupRow(
                            group = group,
                            memberCount = memberCounts[group.internalId],
                            isSelected = group.internalId == selectedGroup?.internalId,
                            onClick = { onSelectGroup(group.internalId) },
                        )
                    }
                }
            } else {
                CollapsedBar(
                    selectedGroup = selectedGroup,
                    isRemoved = isRemoved,
                    onOpenDrawer = onOpenDrawer,
                    onOpenPicker = onOpenPicker,
                    groupMenu = groupMenu,
                )
            }
        }
    }
}

/**
 * The group's name in the middle, between the drawer and the group menu. [isRemoved]: this phone is
 * no longer in the group, said under the name.
 */
@Composable
private fun CollapsedBar(
    selectedGroup: AnonymousGroup?,
    isRemoved: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenPicker: () -> Unit,
    groupMenu: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Servers and settings")
        }
        Row(
            modifier =
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (selectedGroup != null) Modifier.clickable(onClick = onOpenPicker)
                        else Modifier
                    )
                    .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // as wide as the arrow on the other side, so the name itself is centred
            if (selectedGroup != null) Spacer(Modifier.size(24.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = selectedGroup?.name ?: "No groups yet",
                    style =
                        if (selectedGroup != null) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                    color =
                        if (selectedGroup != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isRemoved) {
                    Text(
                        text = "Removed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
            }
            if (selectedGroup != null) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose group")
            }
        }
        if (selectedGroup != null) {
            Box {
                IconButton(onClick = { isMenuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Group menu")
                }
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    groupMenu { isMenuOpen = false }
                }
            }
        } else {
            // balances the drawer button
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun PickerHeader(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
        }
        TextField(
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search groups") },
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search")
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: AnonymousGroup,
    memberCount: Int?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { MemberAvatar(name = group.name, memberId = group.id) },
        headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(groupStatus(group, memberCount)) },
        trailingContent =
            if (isSelected) {
                { Icon(Icons.Default.Check, contentDescription = "Selected") }
            } else null,
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent
            ),
    )
}

/** "Sharing · 3 members", "Not sharing · only you", or why the group can't be used anymore */
internal fun groupStatus(group: AnonymousGroup, memberCount: Int?): String {
    if (!group.existsRemote) return "Deleted"
    if (!group.isMember) return "Removed"
    val sharing = if (group.sendLocation) "Sharing" else "Not sharing"
    val members =
        when (memberCount) {
            null -> null
            1 -> "only you"
            else -> "$memberCount members"
        }
    return listOfNotNull(sharing, members).joinToString(" · ")
}
