package com.peppeosmio.lockate.ui.screens.connection_settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup
import com.peppeosmio.lockate.ui.composables.ErrorDialog
import com.peppeosmio.lockate.ui.composables.FormField
import com.peppeosmio.lockate.ui.composables.MemberAvatar
import com.peppeosmio.lockate.ui.routes.ServerScreenMode
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// long enough to read "Already added" before the map opens on that server
private val NOTICE_BEFORE_NAVIGATING = 1200.milliseconds

/** Connecting the first server, adding another, or editing one: the same fields, three framings */
@Composable
fun ConnectionSettingsScreen(
    mode: ServerScreenMode,
    connectionId: Long?,
    navigateToHome: () -> Unit,
    navigateBack: () -> Unit,
    viewModel: ConnectionSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mode, connectionId) { viewModel.load(mode, connectionId) }
    LaunchedEffect(true) {
        viewModel.navigateHomeEvents.collect { notice ->
            if (notice != null) {
                coroutineScope.launch { snackbarHostState.showSnackbar(notice) }
                delay(NOTICE_BEFORE_NAVIGATING)
            }
            navigateToHome()
        }
    }
    LaunchedEffect(true) { viewModel.navigateBackEvents.collect { navigateBack() } }
    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { message ->
            if (message.errorInfo != null) {
                viewModel.showErrorDialog(message)
            } else {
                snackbarHostState.showSnackbar(message = message.text, withDismissAction = true)
            }
        }
    }

    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }
    if (state.showRemoveDialog) {
        RemoveServerDialog(
            serverName = state.savedName,
            groups = state.groups,
            onConfirm = viewModel::remove,
            onDismiss = viewModel::cancelRemove,
        )
    }

    Scaffold(
        topBar = {
            if (mode != ServerScreenMode.FirstLaunch) {
                ServerTopBar(state, navigateBack = navigateBack, onSave = viewModel::connect)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .then(
                        if (mode == ServerScreenMode.FirstLaunch) Modifier.statusBarsPadding()
                        else Modifier
                    )
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (mode == ServerScreenMode.FirstLaunch) 24.dp else 16.dp
                        ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (mode == ServerScreenMode.FirstLaunch) FirstLaunchIntro()
                Fields(state, viewModel)
                if (mode == ServerScreenMode.Edit) EditExtras(state)
            }
            Footer(state, onConnect = viewModel::connect, onRemove = viewModel::askToRemove)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerTopBar(
    state: ConnectionSettingsState,
    navigateBack: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(if (state.mode == ServerScreenMode.Edit) state.savedName else "Add server")
        },
        navigationIcon = {
            IconButton(onClick = navigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (state.mode == ServerScreenMode.Edit) {
                TextButton(onClick = onSave, enabled = !state.isWorking) { Text("Save") }
            }
        },
    )
}

@Composable
private fun FirstLaunchIntro() {
    Spacer(Modifier.size(48.dp))
    Box(
        modifier =
            Modifier.size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Spacer(Modifier.size(16.dp))
    Text("Connect to a server", style = MaterialTheme.typography.headlineMedium)
    Text(
        "This is the server you use to share your location with others.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(16.dp))
}

@Composable
private fun Fields(state: ConnectionSettingsState, viewModel: ConnectionSettingsViewModel) {
    val address =
        @Composable {
            FormField(
                value = state.url,
                onValueChange = viewModel::onUrlChanged,
                label = "Server address",
                supportingText =
                    if (state.mode == ServerScreenMode.FirstLaunch) {
                        "From whoever runs the server, or your own"
                    } else null,
                enabled = !state.isWorking,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
    val name =
        @Composable {
            FormField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = "Name",
                supportingText = "Any name you like",
                enabled = !state.isWorking,
            )
        }
    when (state.mode) {
        ServerScreenMode.FirstLaunch -> address()
        ServerScreenMode.Add -> {
            address()
            name()
        }
        ServerScreenMode.Edit -> {
            name()
            address()
        }
    }
    FormField(
        value = state.apiKey,
        onValueChange = viewModel::onApiKeyChanged,
        label = if (state.mode == ServerScreenMode.Edit) "API key" else "API key (optional)",
        supportingText = "Only if the server asks for one",
        password = true,
        enabled = !state.isWorking,
    )
}

@Composable
private fun EditExtras(state: ConnectionSettingsState) {
    if (state.groups.isNotEmpty()) {
        Text(
            "Groups on this server",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
        )
        for (group in state.groups) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { MemberAvatar(name = group.name, memberId = group.id) },
                headlineContent = { Text(group.name) },
                supportingContent = {
                    Text(if (group.sendLocation) "Sharing" else "Not sharing")
                },
            )
        }
    }
    state.versionLabel?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Footer(state: ConnectionSettingsState, onConnect: () -> Unit, onRemove: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val hint =
            when (state.mode) {
                ServerScreenMode.FirstLaunch -> "You can add more servers later from the menu"
                ServerScreenMode.Add -> "It opens with no groups. Create or join one there."
                ServerScreenMode.Edit -> removeConsequence(state.groups)
            }
        if (state.mode == ServerScreenMode.Edit) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRemove,
                enabled = !state.isWorking,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Remove from this phone")
            }
        }
        if (state.mode == ServerScreenMode.Add) Hint(hint)
        if (state.mode != ServerScreenMode.Edit) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onConnect,
                enabled = !state.isWorking,
            ) {
                Text(if (state.isWorking) "Connecting…" else "Connect")
            }
        }
        if (state.mode != ServerScreenMode.Add) Hint(hint)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** What removing the server costs: the groups on it, which the others keep */
internal fun removeConsequence(groups: List<AnonymousGroup>): String =
    when (groups.size) {
        0 -> "No groups on this phone use it."
        1 -> "You leave ${groups[0].name}. The others keep the group."
        else -> "You leave ${groups.size} groups. The others keep them."
    }

@Composable
private fun RemoveServerDialog(
    serverName: String,
    groups: List<AnonymousGroup>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text("Remove $serverName from this phone?") },
        text = {
            Text(
                when (groups.size) {
                    0 -> "You can add it again later."
                    else ->
                        "You leave " +
                            groups.joinToString { it.name } +
                            ". Coming back to a group takes a new invite."
                }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Remove")
            }
        },
        onDismissRequest = onDismiss,
    )
}
