package com.peppeosmio.lockate.ui.screens.invite

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.service.invite.InviteCode
import com.peppeosmio.lockate.service.invite.InviteState
import com.peppeosmio.lockate.ui.composables.SmallCircularProgressIndicator
import com.peppeosmio.lockate.utils.ErrorInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    anonymousGroupInternalId: Long,
    navigateBack: () -> Unit,
    viewModel: InviteViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    LaunchedEffect(anonymousGroupInternalId) {
        viewModel.startInvite(anonymousGroupInternalId)
    }

    // The code is read out loud and the exchange polls while this is open
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun copyCode(code: InviteCode) {
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("invite code", code.toString())))
            snackbarHostState.showSnackbar("Code copied to clipboard")
        }
    }

    fun shareCode(code: InviteCode) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, code.toString())
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, topBar = {
        TopAppBar(title = { Text(state.groupName?.let { "Invite to $it" } ?: "Invite") },
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            })
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            when (val inviteState = state.inviteState) {
                InviteState.Creating -> Progress("Creating the invite")

                is InviteState.WaitingForInvitee -> WaitingForInvitee(
                    code = inviteState.code,
                    expiresAt = inviteState.expiresAt,
                    onCopy = { copyCode(inviteState.code) },
                    onShare = { shareCode(inviteState.code) })

                InviteState.Verifying -> Progress("Checking the code")

                InviteState.Sending -> Progress("Sending the group to them")

                InviteState.Done -> Outcome(
                    icon = Icons.Default.CheckCircle,
                    title = "Invite sent",
                    body = "They'll show up in the group as soon as their phone joins.",
                    actionLabel = "Done",
                    onAction = navigateBack
                )

                InviteState.WrongCode -> Outcome(
                    icon = Icons.Default.Warning,
                    title = "The code didn't match",
                    body = "They entered different words, or someone else tried this code first. Nothing was shared, and this code no longer works.",
                    actionLabel = "New code",
                    onAction = { viewModel.startInvite(anonymousGroupInternalId) }
                )

                InviteState.Expired -> Outcome(
                    icon = Icons.Default.Warning,
                    title = "The invite expired",
                    body = "Nobody used the code in time.",
                    actionLabel = "New code",
                    onAction = { viewModel.startInvite(anonymousGroupInternalId) }
                )

                is InviteState.Failed -> {
                    val errorInfo = ErrorInfo.fromException(inviteState.exception)
                    Outcome(
                        icon = Icons.Default.Warning,
                        title = errorInfo.title,
                        body = errorInfo.body,
                        actionLabel = "Try again",
                        onAction = { viewModel.startInvite(anonymousGroupInternalId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Progress(text: String) {
    SmallCircularProgressIndicator()
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun WaitingForInvitee(
    code: InviteCode, expiresAt: Instant, onCopy: () -> Unit, onShare: () -> Unit
) {
    val remaining by produceState(expiresAt - Clock.System.now(), expiresAt) {
        while (value > Duration.ZERO) {
            delay(1.seconds)
            value = (expiresAt - Clock.System.now()).coerceAtLeast(Duration.ZERO)
        }
    }
    Text(
        text = "On the other phone, tap Join and enter this code",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Text(
        // Zero-width spaces after the dashes so a long code wraps between parts, never inside a word
        text = code.toString().replace("-", "-\u200B"),
        style = MaterialTheme.typography.headlineLarge,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onCopy) {
            Icon(
                painterResource(R.drawable.outline_content_copy_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text("Copy", modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Share", modifier = Modifier.padding(start = 8.dp))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallCircularProgressIndicator()
        val seconds = remaining.inWholeSeconds
        Text(
            text = "Waiting for them · expires in ${seconds / 60}:${"%02d".format(seconds % 60)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        text = "Only share it with the person you're inviting: the first one who enters it gets in.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun Outcome(
    icon: ImageVector, title: String, body: String, actionLabel: String, onAction: () -> Unit
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Button(onClick = onAction) { Text(actionLabel) }
}
