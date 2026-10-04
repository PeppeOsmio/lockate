package com.peppeosmio.lockate.ui.screens.manage_connections

import com.peppeosmio.lockate.ui.composables.ErrorDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.peppeosmio.lockate.domain.Connection
import com.peppeosmio.lockate.ui.routes.ConnectionSettingsRoute
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageConnectionsScreen(
    navigateBack: () -> Unit,
    navigateToConnectionSettings: (route: ConnectionSettingsRoute) -> Unit,
    viewModel: ManageConnectionsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(true) {
        viewModel.getInitialData()
    }

    LaunchedEffect(true) {
        viewModel.navigateBackEvents.collect {
            navigateBack()
        }
    }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            if (snackbarMessage.errorInfo != null) {
                viewModel.showErrorDialog(snackbarMessage)
            } else {
                snackbarHostState.showSnackbar(
                    message = snackbarMessage.text, withDismissAction = true
                )
            }
        }
    }

    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }

    if (state.showDeleteConfirmDialog && state.selectedConnectionIdForDelete != null) {
        val connection =
            state.connections!!.first { it.id == state.selectedConnectionIdForDelete }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Delete connection") },
            text = { Text("Are you sure you want to delete ${connection.name.ifBlank { connection.url }}?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConnection() }) { Text("Yes, delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.closeDeleteConfirmDialog()
                    viewModel.unselectForDelete()
                }) { Text("No, keep") }
            })
    }

    if (state.showLoadingOverlay) {
        Dialog(onDismissRequest = {}) {
            CircularProgressIndicator()
        }
    }

    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }, topBar = {
        TopAppBar(title = { Text("Manage connections") }, navigationIcon = {
            IconButton(onClick = { navigateBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
        }, actions = {
            IconButton(onClick = {
                navigateToConnectionSettings(
                    ConnectionSettingsRoute(
                        initialConnectionSettingsId = null, showBackButton = true
                    )
                )
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add new connection")
            }
        })
    }) { paddingValues ->
        val connections = state.connections ?: return@Scaffold
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(connections, key = { it.id!! }) { connection ->
                ManageConnectionRow(
                    connection = connection,
                    onEdit = {
                        navigateToConnectionSettings(
                            ConnectionSettingsRoute(
                                initialConnectionSettingsId = connection.id, showBackButton = true
                            )
                        )
                    },
                    onDelete = {
                        viewModel.selectForDelete(connection.id!!)
                        viewModel.openDeleteConfirmDialog()
                    })
            }
        }
    }
}

@Composable
private fun ManageConnectionRow(
    connection: Connection, onEdit: () -> Unit, onDelete: () -> Unit
) {
    ListItem(headlineContent = { Text(connection.name.ifBlank { connection.url }) },
        supportingContent = { Text(connection.url) },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit connection")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete connection"
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
