package com.peppeosmio.lockate.ui.screens.home_page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity
import com.peppeosmio.lockate.domain.Connection

@Composable
private fun ConnectionRow(
    connection: Connection, isSelected: Boolean, onClick: () -> Unit, onEdit: () -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = isSelected, onCheckedChange = null
        )

        Spacer(Modifier.width(8.dp))

        Text(
            connection.url,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit, contentDescription = "Edit connection"
            )
        }
    }
}


@Composable
fun ConnectionsDialog(
    connections: List<Connection>,
    selectedConnectionId: Long,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    onSelect: (Long) -> Unit,
    onEdit: (Long) -> Unit
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

                // Add new connection
                TextButton(
                    onClick = onAddNew, modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add new connection")
                }

                Spacer(Modifier.height(8.dp))

                // Connections list
                LazyColumn {
                    items(connections.size, key = { connections[it].id!! }) {
                        val connection = connections[it]
                        ConnectionRow(
                            connection = connection,
                            onClick = { onSelect(connection.id!!) },
                            onEdit = { onEdit(connection.id!!) },
                            isSelected = selectedConnectionId == connection.id!!
                        )
                    }
                }
            }
        }
    }
}
