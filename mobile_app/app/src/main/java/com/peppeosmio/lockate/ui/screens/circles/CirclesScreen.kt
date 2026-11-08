package com.peppeosmio.lockate.ui.screens.circles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CirclesScreen(
    anonymousGroupInternalId: Long,
    navigateBack: () -> Unit,
    navigateToCircleEditor: (circleId: String?) -> Unit,
    viewModel: CirclesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(anonymousGroupInternalId) { viewModel.load(anonymousGroupInternalId) }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it.text) }
    }

    state.circleToDelete?.let { circle ->
        AlertDialog(
            title = { Text("Delete ${circle.name}?") },
            text = { Text("The circle disappears for everyone in the group.") },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete, enabled = !state.isDeleting) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, enabled = !state.isDeleting) {
                    Text("Delete")
                }
            },
            onDismissRequest = viewModel::cancelDelete,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Circles")
                        if (state.groupName.isNotEmpty()) {
                            Text(state.groupName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigateToCircleEditor(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add circle") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { SectionTitle("Circles") }
            if (state.circles.isEmpty()) {
                item {
                    Text(
                        "No circles yet",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.circles, key = { it.id }) { circle ->
                ListItem(
                    modifier =
                        Modifier.clickable {
                            viewModel.showOnMap(circle)
                            navigateBack()
                        },
                    headlineContent = {
                        Text(circle.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("${circle.radiusMeters.roundToInt()} m radius") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = circle.id !in state.mutedTargets,
                                onCheckedChange = { viewModel.setTargetMuted(circle.id, !it) },
                            )
                            IconButton(onClick = { navigateToCircleEditor(circle.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${circle.name}")
                            }
                            IconButton(onClick = { viewModel.askToDelete(circle) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${circle.name}",
                                )
                            }
                        }
                    },
                )
            }
            if (state.members.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionTitle("Notify me about")
                }
            }
            items(state.members, key = { "member-${it.id}" }) { member ->
                ListItem(
                    headlineContent = {
                        Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Switch(
                            checked = member.id !in state.mutedTargets,
                            onCheckedChange = { viewModel.setTargetMuted(member.id, !it) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
