package com.peppeosmio.lockate.ui.screens.connection_settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.peppeosmio.lockate.routes.ConnectionSettingsRoute
import com.peppeosmio.lockate.routes.HomeRoute
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectionSettingsScreen(
    initialConnectionSettingsId: Long?,
    navigateToHome: (connectionSettingsId: Long) -> Unit,
    viewModel: ConnectionSettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(true) {
        viewModel.getInitialData(initialConnectionSettingsId)
    }

    // Navigate on success
    LaunchedEffect(true) {
        viewModel.navigateToHome.collect {
            navigateToHome(it)
        }
    }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            val result = snackbarHostState.showSnackbar(
                message = snackbarMessage.text,
                actionLabel = snackbarMessage.errorDialogInfo?.let { "More" },
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
        SnackbarHost(
            hostState = snackbarHostState
        )
    }, content = { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.weight(1.0f))
            Text("Welcome to Lockate", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.url,
                onValueChange = { text -> viewModel.onUrlChanged(text) },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.requireApiKey) {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = { text -> viewModel.onApiKeyChanged(text) },
                    label = { Text("API Key") },
                    singleLine = true,

                    modifier = Modifier.fillMaxWidth()
                )
            }
            FilledTonalButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.onConnectClicked()
                    }
                }, enabled = !state.showLoadingOverlay, modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect")
            }
            Spacer(modifier = Modifier.weight(2.0f))
        }
    })


}
