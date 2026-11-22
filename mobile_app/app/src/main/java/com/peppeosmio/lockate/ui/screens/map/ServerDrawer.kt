package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.Connection

/**
 * The servers this phone uses: each is a context of its own, so switching shows only its groups.
 * The pencil edits a server; Settings is for the whole app.
 */
@Composable
fun ServerDrawer(
    connections: List<Connection>,
    selectedConnectionId: Long?,
    groupCounts: Map<Long, Int>,
    onSelectConnection: (connectionId: Long) -> Unit,
    onEditServer: (connectionId: Long) -> Unit,
    onAddServer: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Text(
            modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
            text = "Server",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            items(connections, key = { it.id!! }) { connection ->
                val groups = groupCounts[connection.id] ?: 0
                NavigationDrawerItem(
                    label = {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(connection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = connection.url,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (groups == 1) "1 group" else "$groups groups",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    icon = {
                        Icon(
                            painterResource(R.drawable.outline_data_table_24),
                            contentDescription = null,
                        )
                    },
                    badge = {
                        IconButton(onClick = { onEditServer(connection.id!!) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${connection.name}")
                        }
                    },
                    selected = connection.id == selectedConnectionId,
                    onClick = { onSelectConnection(connection.id!!) },
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Add server") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    selected = false,
                    onClick = onAddServer,
                )
            }
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    text =
                        "Each server has its own groups. Switching shows only that server's groups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = false,
            onClick = onSettings,
        )
        Spacer(Modifier.size(12.dp))
    }
}
