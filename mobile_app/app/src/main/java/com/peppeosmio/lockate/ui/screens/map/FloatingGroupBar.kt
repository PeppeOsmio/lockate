package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup

/**
 * Pill floating on top of the map. Tapping the group name expands it downwards into the group
 * picker (search field, create/join, scrollable list of groups).
 */
@Composable
fun FloatingGroupBar(
    modifier: Modifier = Modifier,
    selectedGroup: AnonymousGroup?,
    groups: List<AnonymousGroup>,
    isPickerOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectGroup: (anonymousGroupInternalId: Long) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    groupMenu: @Composable (onDismiss: () -> Unit) -> Unit
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            if (isPickerOpen) {
                PickerHeader(query = query, onQueryChange = onQueryChange, onClose = onClosePicker)
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onCreateGroup, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Create")
                    }
                    FilledTonalButton(onClick = onJoinGroup, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Join")
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.6f)) {
                    items(groups, key = { it.internalId }) { group ->
                        GroupRow(
                            group = group,
                            isSelected = group.internalId == selectedGroup?.internalId,
                            onClick = { onSelectGroup(group.internalId) })
                    }
                }
            } else {
                CollapsedBar(
                    selectedGroup = selectedGroup,
                    onOpenDrawer = onOpenDrawer,
                    onOpenPicker = onOpenPicker,
                    groupMenu = groupMenu
                )
            }
        }
    }
}

@Composable
private fun CollapsedBar(
    selectedGroup: AnonymousGroup?,
    onOpenDrawer: () -> Unit,
    onOpenPicker: () -> Unit,
    groupMenu: @Composable (onDismiss: () -> Unit) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Open menu")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenPicker)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = selectedGroup?.name ?: "No groups",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose group")
        }
        if (selectedGroup != null) {
            Box {
                IconButton(onClick = { isMenuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Group options")
                }
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                    groupMenu { isMenuOpen = false }
                }
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun PickerHeader(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
        }
        TextField(
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search groups") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search")
            }
        }
    }
}

@Composable
private fun GroupRow(group: AnonymousGroup, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = when {
            !group.existsRemote -> ({ Text("Group no longer exists") })
            !group.isMember -> ({ Text("You were removed") })
            else -> null
        },
        trailingContent = if (isSelected) {
            { Icon(Icons.Default.Check, contentDescription = "Selected") }
        } else null,
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
