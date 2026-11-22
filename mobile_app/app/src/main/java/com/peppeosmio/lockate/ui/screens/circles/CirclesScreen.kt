package com.peppeosmio.lockate.ui.screens.circles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import com.peppeosmio.lockate.ui.composables.CircleRow
import com.peppeosmio.lockate.ui.composables.MemberAvatar
import com.peppeosmio.lockate.ui.screens.map.occupiedCircles
import com.peppeosmio.lockate.ui.screens.map.whoIsHere
import org.koin.compose.viewmodel.koinViewModel

// the avatars of who is inside a circle stop here, the rest is in the line under the name
private const val MAX_AVATARS = 3

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Circles")
                        if (state.groupName.isNotEmpty()) {
                            Spacer(Modifier.size(12.dp))
                            GroupChip(state.groupName)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    CirclesMenu(
                        enabled = state.circles.isNotEmpty(),
                        onNotifyAll = { viewModel.setAllCirclesMuted(false) },
                        onMuteAll = { viewModel.setAllCirclesMuted(true) },
                    )
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
        val occupied = occupiedCircles(state.circles, state.members)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (state.circles.isEmpty()) {
                item {
                    Text(
                        "No circles yet. Add the places that matter, like home or school, to " +
                            "know when someone arrives or leaves.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                return@LazyColumn
            }
            item {
                SearchField(
                    query = state.query,
                    placeholder =
                        if (state.circles.size == 1) "Search 1 circle"
                        else "Search ${state.circles.size} circles",
                    onQueryChange = viewModel::setQuery,
                )
            }
            item { BellHint() }
            items(state.shownCircles, key = { it.id }) { circle ->
                val inside = occupied[circle].orEmpty()
                CircleListRow(
                    circle = circle,
                    inside = inside,
                    myMemberId = state.myMemberId,
                    isMuted = circle.id in state.mutedTargets,
                    onClick = { navigateToCircleEditor(circle.id) },
                    onMutedChange = { viewModel.setTargetMuted(circle.id, it) },
                )
            }
            if (state.shownCircles.isEmpty()) {
                item {
                    Text(
                        "No circle is called “${state.query.trim()}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupChip(name: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CirclesMenu(enabled: Boolean, onNotifyAll: () -> Unit, onMuteAll: () -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isOpen = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = "Circles menu")
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                text = { Text("Notify for all circles") },
                onClick = {
                    isOpen = false
                    onNotifyAll()
                },
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.outline_notifications_off_24),
                        contentDescription = null,
                    )
                },
                text = { Text("Mute all circles") },
                onClick = {
                    isOpen = false
                    onMuteAll()
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, placeholder: String, onQueryChange: (String) -> Unit) {
    TextField(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else null,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
private fun BellHint() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Tap the bell to be notified when someone arrives or leaves. Only on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CircleListRow(
    circle: Circle,
    inside: List<AGMember>,
    myMemberId: String,
    isMuted: Boolean,
    onClick: () -> Unit,
    onMutedChange: (Boolean) -> Unit,
) {
    CircleRow(
        name = circle.name,
        supporting =
            formatRadius(circle.radiusMeters) +
                " · " +
                (if (inside.isEmpty()) "No one inside" else whoIsHere(inside, myMemberId)),
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (member in inside.take(MAX_AVATARS)) {
                    MemberAvatar(
                        name = member.name,
                        memberId = member.id,
                        isMe = member.id == myMemberId,
                        size = 24.dp,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                if (isMuted) {
                    IconButton(onClick = { onMutedChange(false) }) {
                        Icon(
                            painterResource(R.drawable.outline_notifications_off_24),
                            contentDescription = "Notify about ${circle.name}",
                        )
                    }
                } else {
                    FilledTonalIconButton(onClick = { onMutedChange(true) }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Mute ${circle.name}",
                        )
                    }
                }
            }
        },
    )
}
