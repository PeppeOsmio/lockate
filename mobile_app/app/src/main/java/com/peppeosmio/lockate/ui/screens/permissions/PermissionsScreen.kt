package com.peppeosmio.lockate.ui.screens.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.ui.composables.PreviewSurface
import com.peppeosmio.lockate.ui.composables.ThemePreviews
import com.peppeosmio.lockate.utils.LockatePermission
import org.koin.compose.viewmodel.koinViewModel

/**
 * What Lockate needs and why, one card each. With a [groupInternalId] the screen was opened to
 * share with that group: sharing turns on and the screen closes as soon as both location
 * permissions are on.
 */
@Composable
fun PermissionsScreen(
    groupInternalId: Long?,
    navigateBack: () -> Unit,
    viewModel: PermissionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(groupInternalId) { viewModel.start(groupInternalId) }
    LaunchedEffect(true) { viewModel.doneEvents.collect { navigateBack() } }
    // back from the system settings, or from the system dialog
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refresh()
        }

    fun request(permission: LockatePermission) {
        if (permission in state.asked) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        } else {
            viewModel.onAsked(permission)
            launcher.launch(permission.manifestPermissions.toTypedArray())
        }
    }

    PermissionsContent(
        state = state,
        sharingWaits = groupInternalId != null,
        onRequest = ::request,
        onClose = navigateBack,
    )
}

@Composable
private fun PermissionsContent(
    state: PermissionsState,
    sharingWaits: Boolean,
    onRequest: (LockatePermission) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            IconButton(onClick = onClose, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Permissions",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
                Text(
                    "Lockate needs these to share your location. It uses them only while sharing " +
                        "is on in a group.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
                val precise = state.granted[LockatePermission.PreciseLocation] == true
                for (permission in LockatePermission.asked) {
                    PermissionCard(
                        permission = permission,
                        granted = state.granted[permission] == true,
                        highlighted = state.next == permission,
                        // Android asks for location all the time only once precise location is on
                        canRequest = permission != LockatePermission.LocationAllTheTime || precise,
                        asked = permission in state.asked,
                        onRequest = { onRequest(permission) },
                    )
                }
            }
            if (sharingWaits) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Sharing starts as soon as both location permissions are on. The others " +
                            "are up to you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Not now")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: LockatePermission,
    granted: Boolean,
    highlighted: Boolean,
    canRequest: Boolean,
    asked: Boolean,
    onRequest: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        border = if (highlighted) BorderStroke(1.5.dp, scheme.primary) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier =
                        Modifier.size(40.dp).clip(CircleShape).background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        permission.icon(),
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(permission.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        permission.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (granted) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "On",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                        )
                    }
                }
            }
            if (!granted && canRequest) {
                // the system shows location all the time only in its settings
                val label =
                    if (asked || permission == LockatePermission.LocationAllTheTime) "Open settings"
                    else "Allow"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (highlighted) {
                        Button(onClick = onRequest) { Text(label) }
                    } else {
                        FilledTonalButton(onClick = onRequest) { Text(label) }
                    }
                }
            }
        }
    }
}

private val LockatePermission.title: String
    get() =
        when (this) {
            LockatePermission.PreciseLocation -> "Precise location"
            LockatePermission.LocationAllTheTime -> "Location all the time"
            LockatePermission.Notifications -> "Notifications"
            LockatePermission.PhysicalActivity -> "Physical activity"
        }

private val LockatePermission.description: AnnotatedString
    get() =
        when (this) {
            LockatePermission.PreciseLocation ->
                AnnotatedString("So the group sees where you are, not just the area")
            LockatePermission.LocationAllTheTime ->
                buildAnnotatedString {
                    append("Keeps sharing with the screen off. In Android settings, pick ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Allow all the time")
                    }
                    append(".")
                }
            LockatePermission.Notifications ->
                AnnotatedString(
                    "A quiet notice while you share, and arrivals at circles you follow"
                )
            LockatePermission.PhysicalActivity ->
                AnnotatedString(
                    "Turns the GPS off while you're still, and shows if you walk, cycle or drive"
                )
        }

@Composable
private fun LockatePermission.icon(): Painter =
    when (this) {
        LockatePermission.PreciseLocation -> rememberVectorPainter(Icons.Outlined.LocationOn)
        LockatePermission.LocationAllTheTime -> painterResource(R.drawable.outline_schedule_24)
        LockatePermission.Notifications -> rememberVectorPainter(Icons.Outlined.Notifications)
        LockatePermission.PhysicalActivity ->
            painterResource(R.drawable.baseline_directions_walk_24)
    }

@ThemePreviews
@Composable
private fun PermissionsPreview() {
    PreviewSurface {
        PermissionsContent(
            state =
                PermissionsState(
                    granted =
                        mapOf(
                            LockatePermission.PreciseLocation to true,
                            LockatePermission.LocationAllTheTime to false,
                            LockatePermission.Notifications to false,
                            LockatePermission.PhysicalActivity to true,
                        )
                ),
            sharingWaits = true,
            onRequest = {},
            onClose = {},
        )
    }
}
