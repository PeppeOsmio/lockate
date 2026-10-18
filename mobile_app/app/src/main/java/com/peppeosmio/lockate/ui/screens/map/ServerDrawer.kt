package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

@Composable
fun ServerDrawer(
    connections: List<Connection>,
    selectedConnectionId: Long?,
    unreachableConnectionIds: Set<Long>,
    onSelectConnection: (connectionId: Long) -> Unit,
    onAddServer: () -> Unit,
    onManageServers: () -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet {
        Text(
            modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
            text = "Servers",
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            items(connections, key = { it.id!! }) { connection ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(connection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = connection.url,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.outline_data_table_24), contentDescription = null) },
                    badge = if (connection.id in unreachableConnectionIds) {
                        { UnreachableDot() }
                    } else null,
                    selected = connection.id == selectedConnectionId,
                    onClick = { onSelectConnection(connection.id!!) })
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Add server") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    selected = false,
                    onClick = onAddServer
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            label = { Text("Manage servers") },
            icon = { Icon(painterResource(R.drawable.outline_data_table_24), contentDescription = null) },
            selected = false,
            onClick = onManageServers
        )
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = false,
            onClick = onSettings
        )
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun UnreachableDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}
