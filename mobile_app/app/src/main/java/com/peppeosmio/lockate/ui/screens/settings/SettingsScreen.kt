package com.peppeosmio.lockate.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.service.DEFAULT_GEOCODER_URL
import com.peppeosmio.lockate.ui.composables.FormField
import com.peppeosmio.lockate.ui.composables.InfoCard
import com.peppeosmio.lockate.ui.composables.PreviewSurface
import com.peppeosmio.lockate.ui.composables.ThemePreviews
import com.peppeosmio.lockate.ui.screens.map.DEFAULT_DARK_STYLE_URL
import com.peppeosmio.lockate.ui.screens.map.DEFAULT_LIGHT_STYLE_URL
import com.peppeosmio.lockate.utils.LockatePermission
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    navigateBack: () -> Unit,
    navigateToPermissions: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    SettingsContent(
        state = state,
        onBack = navigateBack,
        onEdit = viewModel::onEdit,
        onPermissions = navigateToPermissions,
        onReset = viewModel::onResetClicked,
    )

    state.draft?.let { draft ->
        UrlDialog(
            draft = draft,
            onTextChange = viewModel::onDraftChanged,
            onSave = viewModel::onSaveDraft,
            onDismiss = viewModel::onDismissDraft,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsState,
    onBack: () -> Unit,
    onEdit: (SettingsUrl) -> Unit,
    onPermissions: () -> Unit,
    onReset: () -> Unit,
) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionTitle("Map")
                for (url in SettingsUrl.entries) {
                    ListItem(
                        modifier = Modifier.clickable { onEdit(url) },
                        colors = transparent,
                        leadingContent = { Icon(url.icon(), contentDescription = null) },
                        headlineContent = { Text(url.title) },
                        supportingContent = {
                            Text(
                                urlSummary(url, state.urls[url]),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
                InfoCard(
                    text =
                        "Map tiles and searches go straight from this phone to these services. " +
                            "They can tell which area you look at.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                SectionTitle("This phone")
                ListItem(
                    modifier = Modifier.clickable(onClick = onPermissions),
                    colors = transparent,
                    leadingContent = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                    headlineContent = { Text("Permissions") },
                    supportingContent = { Text(permissionsSummary(state.permissions)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )
            }
            TextButton(onClick = onReset, modifier = Modifier.padding(8.dp)) {
                Text("Reset map and search to defaults")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun UrlDialog(
    draft: UrlDraft,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.url.title) },
        text = {
            Column {
                Text(
                    draft.url.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                FormField(
                    value = draft.text,
                    onValueChange = onTextChange,
                    label = "URL",
                    supportingText = "Default: ${draft.url.default}",
                    errorText = if (draft.isError) draft.url.errorText else null,
                    enabled = !draft.isChecking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !draft.isChecking) {
                Text(if (draft.isChecking) "Checking…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val SettingsUrl.title: String
    get() =
        when (this) {
            SettingsUrl.LightStyle -> "Light map style"
            SettingsUrl.DarkStyle -> "Dark map style"
            SettingsUrl.PlaceSearch -> "Place search"
        }

private val SettingsUrl.explanation: String
    get() =
        when (this) {
            SettingsUrl.LightStyle,
            SettingsUrl.DarkStyle -> "Any MapLibre style URL. Leave it empty for the default."
            SettingsUrl.PlaceSearch ->
                "A Photon geocoder, used to search places when you add or edit a circle. Leave " +
                    "it empty for the default."
        }

private val SettingsUrl.default: String
    get() =
        when (this) {
            SettingsUrl.LightStyle -> DEFAULT_LIGHT_STYLE_URL
            SettingsUrl.DarkStyle -> DEFAULT_DARK_STYLE_URL
            SettingsUrl.PlaceSearch -> DEFAULT_GEOCODER_URL
        }

private val SettingsUrl.errorText: String
    get() =
        when (this) {
            SettingsUrl.PlaceSearch ->
                "This isn't a Photon geocoder. Check the URL and your connection."
            else -> "This isn't a valid map style. Check the URL and your connection."
        }

@Composable
private fun SettingsUrl.icon(): Painter =
    when (this) {
        SettingsUrl.LightStyle -> painterResource(R.drawable.outline_light_mode_24)
        SettingsUrl.DarkStyle -> painterResource(R.drawable.outline_dark_mode_24)
        SettingsUrl.PlaceSearch -> rememberVectorPainter(Icons.Outlined.Search)
    }

@ThemePreviews
@Composable
private fun SettingsPreview() {
    PreviewSurface {
        SettingsContent(
            state =
                SettingsState(
                    urls =
                        mapOf(SettingsUrl.DarkStyle to "https://tiles.mymaps.example/styles/dark"),
                    permissions =
                        mapOf(
                            LockatePermission.PreciseLocation to true,
                            LockatePermission.LocationAllTheTime to true,
                            LockatePermission.Notifications to true,
                        ),
                ),
            onBack = {},
            onEdit = {},
            onPermissions = {},
            onReset = {},
        )
    }
}
