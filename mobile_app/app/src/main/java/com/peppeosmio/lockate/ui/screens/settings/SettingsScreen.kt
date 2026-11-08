package com.peppeosmio.lockate.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.service.DEFAULT_GEOCODER_URL
import com.peppeosmio.lockate.ui.screens.map.DEFAULT_DARK_STYLE_URL
import com.peppeosmio.lockate.ui.screens.map.DEFAULT_LIGHT_STYLE_URL
import org.koin.compose.viewmodel.koinViewModel

private const val STYLE_ERROR_TEXT =
    "This isn't a valid map style. Check the URL and your connection."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigateBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(true) {
        viewModel.navigateBackEvents.collect { navigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Map style", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use any MapLibre style URL. Leave a field empty for the default. The map provider can see the area you're looking at.",
                style = MaterialTheme.typography.bodyMedium,
            )
            UrlField(
                label = "Light map style URL",
                placeholder = DEFAULT_LIGHT_STYLE_URL,
                value = state.lightStyleUrl,
                isError = state.lightStyleError,
                errorText = STYLE_ERROR_TEXT,
                onValueChange = viewModel::onLightStyleUrlChanged,
            )
            UrlField(
                label = "Dark map style URL",
                placeholder = DEFAULT_DARK_STYLE_URL,
                value = state.darkStyleUrl,
                isError = state.darkStyleError,
                errorText = STYLE_ERROR_TEXT,
                onValueChange = viewModel::onDarkStyleUrlChanged,
            )
            Text("Place search", style = MaterialTheme.typography.titleMedium)
            Text(
                "Searching places when you add or edit a circle uses a Photon geocoder. Leave the field empty for the default. The geocoder can see what you search and the area you're looking at.",
                style = MaterialTheme.typography.bodyMedium,
            )
            UrlField(
                label = "Photon URL",
                placeholder = DEFAULT_GEOCODER_URL,
                value = state.geocoderUrl,
                isError = state.geocoderError,
                errorText = "This isn't a Photon geocoder. Check the URL and your connection.",
                onValueChange = viewModel::onGeocoderUrlChanged,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::onResetClicked,
                    enabled = !state.isSaving,
                ) {
                    Text("Reset to default")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::onSaveClicked,
                    enabled = !state.isSaving,
                ) {
                    Text(if (state.isSaving) "Checking…" else "Save")
                }
            }
        }
    }
}

@Composable
private fun UrlField(
    label: String,
    placeholder: String,
    value: String,
    isError: Boolean,
    errorText: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1) },
        isError = isError,
        supportingText =
            if (isError) {
                { Text(errorText) }
            } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
    )
}
