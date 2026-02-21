package com.peppeosmio.lockate.ui.screens.anonymous_groups

import android.content.ClipData
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
    navigateToAGDetails: (anonymousGroupInternalId: Long, anonymousGroupName: String) -> Unit,
    registerOnFabTap: (onClickFab: () -> Unit) -> Unit,
    unregisterOnFabTap: () -> Unit,
    registerOnSearch: (onSearch: (query: String) -> Unit) -> Unit,
    unregisterOnSearch: () -> Unit,
    showErrorSnackbar: (snackbarErrorMessage: SnackbarErrorMessage) -> Unit,
    viewModel: AnonymousGroupsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val agSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val localClipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

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
            coroutineScope.launch {
                viewModel.closeAddBottomSheet()
                addSheetState.hide()
            }
        }, onTapCreate = {
            coroutineScope.launch {
                viewModel.closeAddBottomSheet()
                addSheetState.hide()
                navigateToCreateAG()
            }
        }, onTapJoin = {
            coroutineScope.launch {
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
                coroutineScope.launch {
                    viewModel.openSureLeaveDialog()
                    agSheetState.hide()
                }
            },
            onTapRemove = {
                coroutineScope.launch {
                    viewModel.removeAnonymousGroup()
                    viewModel.unselectAnonymousGroup()
                    agSheetState.hide()
                }
            },
            onTapCopy = {
                coroutineScope.launch {
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
                    viewModel.leaveAnonymousGroup(connectionSettingsId)
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

    PullToRefreshBox(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) {
                focusManager.clearFocus()
            },
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.getInitialData(connectionSettingsId) },
    ) {
        val anonymousGroups = state.anonymousGroups ?: return@PullToRefreshBox
        LazyColumn() {
            item { Spacer(Modifier.size(8.dp)) }
            itemsIndexed(
                anonymousGroups,
                key = { _, agWithMembersCount -> agWithMembersCount.id }) { i, anonymousGroup ->
                AnonymousGroupCard(
                    modifier = Modifier.animateItem(),
                    anonymousGroup = anonymousGroup,
                    onClick = {
                        if (anonymousGroup.existsRemote && anonymousGroup.isMember) {
                            navigateToAGDetails(anonymousGroup.internalId, anonymousGroup.name)
                        }
                    },
                    onMoreOptions = { viewModel.selectAnonymousGroup(i) })
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}
