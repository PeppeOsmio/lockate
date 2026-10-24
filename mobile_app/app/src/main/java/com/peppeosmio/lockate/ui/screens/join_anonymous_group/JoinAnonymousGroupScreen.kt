package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import com.peppeosmio.lockate.ui.composables.ErrorDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinAnonymousGroupScreen(
    connectionId: Long,
    navigateBack: () -> Unit,
    viewModel: JoinAnonymousGroupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            if (snackbarMessage.errorInfo != null) {
                viewModel.showErrorDialog(snackbarMessage)
            } else {
                snackbarHostState.showSnackbar(
                    message = snackbarMessage.text,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    LaunchedEffect(true) {
        viewModel.navigateBackEvents.collect {
            navigateBack()
        }
    }

    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }

    if (state.isJoining) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Joining") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Waiting for the other phone. Keep the invite open on it.")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::cancelJoin) { Text("Cancel") } })
    }

    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }, topBar = {
        TopAppBar(title = { Text("Join with an invite code") }, navigationIcon = {
            IconButton(onClick = {
                navigateBack()
            }) {
                Icon(
                    imageVector = Icons.Default.Close, contentDescription = "Close"
                )
            }
        }, actions = {
            IconButton(onClick = {
                focusManager.clearFocus()
                viewModel.joinAnonymousGroup(connectionId)
            }) {
                Icon(
                    imageVector = Icons.Default.Done, contentDescription = "Done"
                )
            }
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null) {
                    focusManager.clearFocus()
                },
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = "Ask a member of the group to open Invite and read you the code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.codeText,
                onValueChange = { text -> viewModel.setCodeText(text) },
                label = { Text("Invite code") },
                placeholder = { Text("7-word-word-word") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii
                ),
                isError = state.codeError != null,
                supportingText = {
                    if (state.codeError != null) {
                        Text(text = state.codeError!!, color = MaterialTheme.colorScheme.error)
                    }
                })
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.memberNameText,
                onValueChange = { text -> viewModel.setUserNameText(text) },
                label = { Text("Your member name") },
                isError = state.memberNameError != null,
                supportingText = {
                    if (state.memberNameError != null) {
                        Text(
                            text = state.memberNameError!!, color = MaterialTheme.colorScheme.error
                        )
                    }
                })
        }
    }
}