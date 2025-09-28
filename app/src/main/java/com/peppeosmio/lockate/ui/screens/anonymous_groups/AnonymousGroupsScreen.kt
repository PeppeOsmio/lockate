package com.peppeosmio.lockate.ui.screens.anonymous_groups

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.peppeosmio.lockate.utils.SnackbarErrorMessage
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnonymousGroupsScreen(
    connectionSettingsId: Long,
    navigateToCreateAG: () -> Unit,
    navigateToJoinAG: () -> Unit,
    navigateToAGDetails : (anonymousGroupId: String, anonymousGroupName: String) -> Unit,
    registerOnFabTap: (onClickFab: () -> Unit) -> Unit,
    unregisterOnFabTap: () -> Unit,
    registerOnSearch: (onSearch: (query: String) -> Unit) -> Unit,
    unregisterOnSearch: () -> Unit,
    showErrorSnackbar: (snackbarErrorMessage: SnackbarErrorMessage ) -> Unit,
    viewModel: AnonymousGroupsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    val scope = rememberCoroutineScope()
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val agSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val localClipboard = LocalClipboard.current

    DisposableEffect(true) {
        registerOnFabTap(viewModel::onFabTap)
        registerOnSearch(viewModel::onSearch)
        onDispose {
            unregisterOnFabTap()
            unregisterOnSearch()
        }
    }

    LaunchedEffect(true) {
        viewModel.getInitialData(connectionSettingsId)
    }

    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { snackbarMessage ->
            showErrorSnackbar(snackbarMessage)
        }
    }

    LaunchedEffect(connectionSettingsId) {
        viewModel.getInitialData(connectionSettingsId)
    }

    // Add group bottom sheet
    if (state.showAddBottomSheet) {
        AddAGBottomSheet(sheetState = addSheetState, onDismiss = {
            scope.launch {
                viewModel.closeAddBottomSheet()
                addSheetState.hide()
            }
        }, onTapCreate = {
            scope.launch {
                viewModel.closeAddBottomSheet()
                addSheetState.hide()
                navigateToCreateAG()
            }
        }, onTapJoin = {
            scope.launch {
                viewModel.closeAddBottomSheet()
                addSheetState.hide()
                navigateToJoinAG()
            }
        })
    }

    // Group actions bottom sheet
    if (state.selectedAGIndex != null) {
        AGActionsBottomSheet(
            sheetState = agSheetState,
            onDismiss = { viewModel.unselectAnonymousGroup() },
            anonymousGroup = state.anonymousGroups!![state.selectedAGIndex!!],
            onTapLeave = {
                scope.launch {
                    viewModel.openSureLeaveDialog()
                    agSheetState.hide()
                }
            },
            onTapRemove = {
                scope.launch {
                    viewModel.removeAnonymousGroup()
                    viewModel.unselectAnonymousGroup()
                    agSheetState.hide()
                }
            },
            onTapCopy = {
                scope.launch {
                    localClipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                "anonymousGroupId",
                                state.anonymousGroups!![state.selectedAGIndex!!].id
                            )
                        )
                    )
                }
            })
    }

    // Sure leave dialog
    if (state.showSureLeaveDialog && state.selectedAGIndex != null) {
        val agWithMembersCount = state.anonymousGroups!![state.selectedAGIndex!!]
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Leave anonymous group") },
            text = { Text("Are you sure you want to leave ${agWithMembersCount.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.leaveAnonymousGroup(connectionSettingsId)
                        viewModel.unselectAnonymousGroup()
                    }
                }) { Text("Yes, leave") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.closeSureLeaveDialog()
                    viewModel.unselectAnonymousGroup()
                }) { Text("No, stay") }
            })
    }

    if (state.showLoadingOverlay) {
        Dialog(onDismissRequest = {}) {
            CircularProgressIndicator()
        }
    }

    if (state.anonymousGroups == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null) {
                    focusManager.clearFocus()
                }) {
            item { Spacer(Modifier.size(8.dp)) }
            itemsIndexed(
                state.anonymousGroups!!,
                key = { _, agWithMembersCount -> agWithMembersCount.id }) { i, anonymousGroup ->
                AnonymousGroupCard(
                    modifier = Modifier.animateItem(),
                    anonymousGroup = anonymousGroup, onClick = {
                        if(anonymousGroup.existsRemote && anonymousGroup.isMember) {
                            navigateToAGDetails(anonymousGroup.id, anonymousGroup.name)
                        }
                }, onMoreOptions = { viewModel.selectAnonymousGroup(i) })
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}
