package com.peppeosmio.lockate.ui.screens.create_anonymous_group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.ui.composables.ErrorDialog
import com.peppeosmio.lockate.ui.composables.FormField
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAnonymousGroupScreen(
    connectionSettingsId: Long,
    navigateBack: () -> Unit,
    viewModel: CreateAnonymousGroupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { message ->
            if (message.errorInfo != null) {
                viewModel.showErrorDialog(message)
            } else {
                snackbarHostState.showSnackbar(message = message.text, withDismissAction = true)
            }
        }
    }
    LaunchedEffect(true) { viewModel.navigateBackEvents.collect { navigateBack() } }

    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New group") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        // the scaffold already pads for the navigation bar, which the keyboard covers
        Column(
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FormField(
                    value = state.groupNameText,
                    onValueChange = viewModel::setGroupNameText,
                    label = "Group name",
                    errorText = state.groupNameError,
                    enabled = !state.showLoadingOverlay,
                )
                FormField(
                    value = state.userNameText,
                    onValueChange = viewModel::setUserNameText,
                    label = "Your name in this group",
                    errorText = state.userNameError,
                    enabled = !state.showLoadingOverlay,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                enabled = !state.showLoadingOverlay,
                onClick = {
                    focusManager.clearFocus()
                    viewModel.createAnonymousGroup(connectionSettingsId)
                },
            ) {
                Text(if (state.showLoadingOverlay) "Creating…" else "Create group")
            }
        }
    }
}
