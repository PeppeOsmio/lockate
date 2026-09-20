package com.peppeosmio.lockate.ui.screens.home_page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.peppeosmio.lockate.domain.Connection

@Composable
private fun ConnectionRow(
    connection: Connection, isSelected: Boolean, onClick: () -> Unit
) {
    val connectionLabel = connection.name.ifBlank { connection.url }
    Row(modifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
        .clickable { onClick() }
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = isSelected, onClick = null
        )

        Spacer(Modifier.width(8.dp))

        Text(
            connectionLabel,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun ConnectionsDialog(
    connections: List<Connection>,
    selectedConnectionId: Long,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                // Title
                Text(
                    text = "Connections", style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Add / manage connections
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onAddNew, modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add new")
                    }
                    TextButton(
                        onClick = onManage, modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Manage")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Connections list
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(connections.size, key = { connections[it].id!! }) {
                        val connection = connections[it]
                        ConnectionRow(
                            connection = connection,
                            onClick = { onSelect(connection.id!!) },
                            isSelected = selectedConnectionId == connection.id!!
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
