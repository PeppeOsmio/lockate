package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Whether this phone shares its location with the group; the whole row toggles the switch */
@Composable
fun SharingRow(
    sharing: Boolean,
    onSharingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier =
            modifier.toggleable(
                value = sharing,
                role = Role.Switch,
                onValueChange = onSharingChange,
            ),
        colors =
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint =
                    if (sharing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(if (sharing) "Sharing your location" else "Not sharing your location")
        },
        trailingContent = { Switch(checked = sharing, onCheckedChange = null) },
    )
}

@ThemePreviews
@Composable
private fun SharingRowPreview() {
    PreviewSurface {
        Column {
            SharingRow(sharing = true, onSharingChange = {})
            SharingRow(sharing = false, onSharingChange = {})
        }
    }
}
