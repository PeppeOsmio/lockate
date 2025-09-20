package com.peppeosmio.lockate.ui.screens.create_anonymous_group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAnonymousGroupScreen(
    connectionSettingsId: Long,
    navigateBack: () -> Unit,
    viewModel: CreateAnonymousGroupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            val result = snackbarHostState.showSnackbar(
                message = snackbarMessage.text,
                snackbarMessage.errorDialogInfo?.let { "More" },
                withDismissAction = true
            )
            when (result) {
                SnackbarResult.Dismissed -> Unit
                SnackbarResult.ActionPerformed -> snackbarMessage.errorDialogInfo?.let {
                    viewModel.showErrorDialog(it)
                }
            }
        }
    }

    state.errorDialogInfo?.let {
        AlertDialog(title = { Text(it.title) }, text = { Text(it.body) }, dismissButton = {
            TextButton(onClick = { viewModel.hideErrorDialog() }) { Text("Dismiss") }
        }, confirmButton = {}, onDismissRequest = { viewModel.hideErrorDialog() })
    }

    if (state.showLoadingOverlay) {
        Dialog(onDismissRequest = {}) {
            CircularProgressIndicator()
        }
    }

    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }, topBar = {
        TopAppBar(title = { Text("Create anonymous group") }, navigationIcon = {
            IconButton(onClick = {
                navigateBack()
            }) {
                Icon(
                    imageVector = Icons.Default.Close, contentDescription = "Back"
                )
            }
        }, actions = {
            IconButton(onClick = {
                scope.launch {
                    val anonymousGroupId = viewModel.createAnonymousGroup(connectionSettingsId)
                    if (anonymousGroupId != null) {
                        navigateBack()
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Done, contentDescription = "Back"
                )
            }
        })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.groupNameText,
                onValueChange = { text -> viewModel.setGroupNameText(text) },
                label = { Text("Anonymous group name") },
                isError = state.groupNameError != null,
                supportingText = {
                    if (state.groupNameError != null) {
                        Text(
                            text = state.groupNameError!!, color = MaterialTheme.colorScheme.error,
                        )
                    }
                })
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.memberPasswordText,
                onValueChange = { text -> viewModel.setMemberPasswordText(text) },
                label = { Text("Member password") },
                isError = state.memberPasswordError != null,
                supportingText = {
                    if (state.memberPasswordError != null) {
                        Text(
                            text = state.memberPasswordError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                })
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.adminPasswordText,
                onValueChange = { text -> viewModel.setAdminPasswordText(text) },
                label = { Text("Admin password") },
                isError = state.adminPasswordError != null,
                supportingText = {
                    if (state.adminPasswordError != null) {
                        Text(
                            text = state.adminPasswordError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                })
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.userNameText,
                onValueChange = { text -> viewModel.setUserNameText(text) },
                label = { Text("Your member name") },
                isError = state.userNameError != null,
                supportingText = {
                    if (state.userNameError != null) {
                        Text(
                            text = state.userNameError!!, color = MaterialTheme.colorScheme.error
                        )
                    }
                })
        }
    }
}