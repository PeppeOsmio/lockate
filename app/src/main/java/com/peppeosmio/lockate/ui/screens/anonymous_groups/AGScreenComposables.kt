package com.peppeosmio.lockate.ui.screens.anonymous_groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.utils.DateTimeUtils
import kotlinx.datetime.format

/**
 * Modal bottom sheet to create or join an anonymous group
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAGBottomSheet(
    sheetState: SheetState, onDismiss: () -> Unit, onTapCreate: () -> Unit, onTapJoin: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = "Choose an action",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Default.Add, contentDescription = "Create anonymous group"
                )
            },
            headlineContent = { Text("Create anonymous group") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onTapCreate()
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Default.Person, contentDescription = "Join anonymous group"
                )
            },
            headlineContent = { Text("Join anonymous group") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onTapJoin()
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Modal bottom sheet with actions for a selected anonymous group
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AGActionsBottomSheet(
    sheetState: SheetState,
    anonymousGroup: AnonymousGroup,
    onDismiss: () -> Unit,
    onTapLeave: () -> Unit,
    onTapRemove: () -> Unit,
    onTapCopy: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = anonymousGroup.name,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))

        if (anonymousGroup.isMember && anonymousGroup.existsRemote) {
            ListItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.outline_logout_24),
                        contentDescription = "Leave anonymous group"
                    )
                },
                headlineContent = { Text("Leave") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onTapLeave()
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        } else {
            ListItem(
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove anonymous group"
                    )
                },
                headlineContent = { Text("Remove") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onTapRemove()
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.outline_content_copy_24),
                    contentDescription = "Copy anonymous group id"
                )
            },
            headlineContent = { Text("Copy anonymous group id") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onTapCopy()
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Card representing a single anonymous group in the list
 */
@Composable
fun AnonymousGroupCard(
    modifier: Modifier,
    anonymousGroup: AnonymousGroup,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anonymousGroup.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!anonymousGroup.isMember) {
                        Text(
                            text = "You were removed from this anonymous group",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!anonymousGroup.existsRemote) {
                        Text(
                            text = "This anonymous group doesn't exist anymore",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(
                    onClick = onMoreOptions
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Anonymous group options")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Text(
                text = "Your name: ${anonymousGroup.memberName}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Joined at: ${anonymousGroup.joinedAt.format(DateTimeUtils.DATE_FORMAT)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Created at: ${
                    anonymousGroup.createdAt.format(
                        DateTimeUtils.DATE_FORMAT
                    )
                }", style = MaterialTheme.typography.bodyMedium
            )
        }

    }
}
